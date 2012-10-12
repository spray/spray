/*
 * Copyright (C) 2011-2012 spray.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package spray.io

import java.util.concurrent.CountDownLatch
import java.nio.channels.spi.SelectorProvider
import java.nio.ByteBuffer
import java.nio.channels.{CancelledKeyException, SelectionKey, SocketChannel, ServerSocketChannel}
import java.net.InetSocketAddress
import annotation.tailrec
import akka.event.{LoggingBus, BusLogging, LoggingAdapter}
import akka.actor.{Status, ActorSystem, ActorRef}
import akka.util.NonFatal
import spray.util._


// threadsafe
class IOBridge(log: LoggingAdapter, settings: IOBridgeSettings) {
  def this(bus: LoggingBus, settings: IOBridgeSettings) = this(new BusLogging(bus, "IOBridge", classOf[IOBridge]), settings)
  def this(loggingSystem: ActorSystem, settings: IOBridgeSettings) = this(loggingSystem.eventStream, settings)
  def this(loggingSystem: ActorSystem) = this(loggingSystem, IOBridgeSettings())

  import IOBridge._

  private[this] var ioThread: IOThread = _

  /**
   * @return the IO thread if started and not yet stopped, otherwise None
   */
  def thread: Option[Thread] = lock.synchronized(Option(ioThread))

  /**
   * Starts the IOBridge if not yet started
   * @return this instance
   */
  def start(): this.type = {
    lock.synchronized {
      if (ioThread == null) {
        ioThread = new IOThread(settings, log)
        ioThread.start()
        _runningBridges = _runningBridges :+ this
      }
    }
    this
  }

  /**
   * Stops the IOBridge if not yet stopped.
   * The method blocks until the IOBridge has been successfully terminated.
   * It can be restarted by calling start().
   */
  def stop() {
    lock.synchronized {
      if (ioThread != null) {
        val latch = new CountDownLatch(1)
        this ! Stop(latch)
        latch.await()
        ioThread = null
        _runningBridges = _runningBridges.filter(_ != this)
      }
    }
  }

  /**
   * Posts a Command to the IOBridges command queue.
   */
  def ! (cmd: Command)(implicit sender: ActorRef = null) {
    if (ioThread == null) throw new IllegalStateException("Cannot post message to unstarted IOBridge")
    ioThread.post(cmd, sender)
  }

  /**
   * Posts a Command to the IOBridges command queue.
   */
  def tell(cmd: Command, sender: ActorRef) {
    this.!(cmd)(sender)
  }

  private class IOThread(settings: IOBridgeSettings, log: LoggingAdapter) extends Thread {
    import SelectionKey._

    private val commandQueue = new SingleReaderConcurrentQueue[(Command, ActorRef)]
    private val selector = SelectorProvider.provider.openSelector
    private var stopped: CountDownLatch = _

    // stats fields
    private var startTime = 0L
    private var bytesRead = 0L
    private var bytesWritten = 0L
    private var connectionsOpened = 0L
    private var connectionsClosed = 0L
    private var commandsExecuted = 0L

    setName(settings.ThreadName + '-' + _runningBridges.size)
    setDaemon(true)

    override def start() {
      if (getState == Thread.State.NEW) {
        startTime = System.currentTimeMillis
        super.start()
      }
    }

    // executed from other threads!
    def post(cmd: Command, sender: ActorRef) {
      commandQueue.enqueue((cmd, sender))
      selector.wakeup()
    }

    override def run() {
      log.info("IOBridge thread '{}' started", Thread.currentThread.getName)
      while (stopped == null) {
        if (commandQueue.isEmpty) {
          select()
        } else {
          val cmdInstance = commandQueue.dequeue()
          runCommand(cmdInstance._1, cmdInstance._2)
          commandsExecuted += 1
        }
      }
      closeSelector()
      log.info("IOBridge thread '{}' stopped", Thread.currentThread.getName)
      stopped.countDown()
    }

    def select() {
      // The following select() call only really blocks for a longer period of time if the commandQueue is empty.
      // Otherwise selector.wakeup() will either already have been called (or will be called shortly), which causes
      // the following call to not block at all or for long.
      selector.select()
      val keys = selector.selectedKeys.iterator
      while (keys.hasNext) {
        val key = keys.next
        keys.remove()
        if (key.isValid) {
          if (key.isWritable) write(key) // prefer writing over reading if both ops are ready
          else if (key.isReadable) read(key)
          else if (key.isAcceptable) accept(key)
          else if (key.isConnectable) connect(key)
        } else log.warning("Invalid selection key: {}", key)
      }
    }

    def write(key: SelectionKey) {
      log.debug("Writing to connection")
      val handle = key.attachment.asInstanceOf[Handle]
      val channel = key.channel.asInstanceOf[SocketChannel]
      val oldBytesWritten = bytesWritten
      val writeQueue = handle.key.writeQueue

      @tailrec
      def writeToChannel() {
        if (!writeQueue.isEmpty) {
          writeQueue.dequeue() match {
            case buf: ByteBuffer =>
              bytesWritten += channel.write(buf)
              if (buf.remaining == 0) writeToChannel() // we continue with the next buffer
              else {
                buf +=: writeQueue // we cannot drop the head and need to continue with it next time
                log.debug("Wrote {} bytes, more pending", bytesWritten - oldBytesWritten)
              }
            case Ack(receiver, msg) =>
              receiver ! msg
              writeToChannel()
            case PerformCleanClose => close(handle, CleanClose)
          }
        } else {
          handle.key.disable(OP_WRITE)
          log.debug("Wrote {} bytes", bytesWritten - oldBytesWritten)
        }
      }

      try writeToChannel()
      catch {
        case NonFatal(e) =>
          log.warning("Write error: closing connection due to {}", e.toString)
          close(handle, IOError(e))
      }
    }

    def read(key: SelectionKey) {
      log.debug("Reading from connection")
      val handle = key.attachment.asInstanceOf[Handle]
      val channel = key.channel.asInstanceOf[SocketChannel]
      val buffer = ByteBuffer.allocate(settings.ReadBufferSize.toInt)

      try {
        if (channel.read(buffer) > -1) {
          buffer.flip()
          log.debug("Read {} bytes", buffer.limit)
          bytesRead += buffer.limit
          handle.handler ! Received(handle, buffer)
        } else {
          // if the peer shut down the socket cleanly, we do the same
          close(handle, PeerClosed)
        }
      } catch {
        case NonFatal(e) =>
          log.warning("Read error: closing connection due to {}", e.toString)
          close(handle, IOError(e))
      }
    }

    def accept(key: SelectionKey) {
      try {
        val channel = key.channel.asInstanceOf[ServerSocketChannel].accept()
        configure(channel)
        val connectionKey = channel.register(selector, 0) // we don't enable any ops until we have a handle
        val cmd = key.attachment.asInstanceOf[Bind]
        log.debug("New connection accepted on {}", cmd.address)
        val remoteAddress = channel.socket.getRemoteSocketAddress.asInstanceOf[InetSocketAddress]
        val localAddress = channel.socket.getLocalSocketAddress.asInstanceOf[InetSocketAddress]
        cmd.handleCreator ! Connected(Key(connectionKey), remoteAddress, localAddress, cmd.tag)
      } catch {
        case NonFatal(e) => log.error(e, "Accept error: could not accept new connection")
      }
    }

    def configure(channel: SocketChannel) {
      val socket = channel.socket
      // tcpReceiveBufferSize needs to be set on the ServerSocket before the bind, so we don't set it here
      if (settings.TcpSendBufferSize != 0) socket.setSendBufferSize(settings.TcpSendBufferSize.toInt)
      if (settings.TcpKeepAlive != 0)      socket.setKeepAlive(settings.TcpKeepAlive > 0)
      if (settings.TcpNoDelay != 0)        socket.setTcpNoDelay(settings.TcpNoDelay > 0)
      channel.configureBlocking(false)
    }

    def connect(key: SelectionKey) {
      val (cmd@Connect(address, _, tag), sender) = key.attachment.asInstanceOf[(Connect, ActorRef)]
      try {
        val channel = key.channel.asInstanceOf[SocketChannel]
        channel.finishConnect()
        key.interestOps(0) // we don't enable any ops until we have a handle
        log.debug("Connection established to {}", address)
        val localAddress = channel.socket.getLocalSocketAddress.asInstanceOf[InetSocketAddress]
        sender ! Connected(Key(key), address, localAddress, tag)
      } catch {
        case NonFatal(e) =>
          log.error(e, "Connect error: could not establish new connection to {}", address)
          sender ! Status.Failure(CommandException(cmd, e))
      }
    }

    def runCommand(command: Command, sender: ActorRef) {
      try {
        log.debug("Executing command {}", command)
        command match {
          // ConnectionCommands
          case x: Send => send(x, sender)
          case x: StopReading => x.handle.key.disable(OP_READ)
          case x: ResumeReading => x.handle.key.enable(OP_READ)
          case x: Register => register(x)
          case x: Close => scheduleClose(x.handle, x.reason)

          // SuperCommands
          case x: Connect => connect(x, sender)
          case x: Bind => bind(x, sender)
          case x: Unbind => unbind(x, sender)
          case GetStats => deliverStats(sender)
          case Stop(latch) => stopped = latch

          case x => log.warning("Received unknown command '{}', ignoring ...", x)
        }
      } catch {
        case e: CancelledKeyException if command.isInstanceOf[ConnectionCommand] =>
          log.warning("Could not execute command '{}': connection reset by peer", command)
          val handle = command.asInstanceOf[ConnectionCommand].handle
          handle.handler ! Closed(handle, PeerClosed)
        case e =>
          log.error(e, "Error during execution of command '{}'", command)
          if (sender != null) sender ! Status.Failure(CommandException(command, e))
      }
    }

    def send(cmd: Send, sender: ActorRef) {
      val key = cmd.handle.key
      key.writeQueue ++= cmd.buffers
      if (cmd.ack.isDefined && sender != null) key.writeQueue += Ack(sender, cmd.ack.get)
      key.enable(OP_WRITE)
    }

    def register(cmd: Register) {
      val key = cmd.handle.key
      key.selectionKey.attach(cmd.handle)
      key.enable(OP_READ) // always start out in reading mode
      connectionsOpened += 1
    }

    def scheduleClose(handle: Handle, reason: ConnectionClosedReason) {
      val key = handle.key.selectionKey
      if (key.isValid) {
        if (reason == CleanClose && !handle.key.writeQueue.isEmpty) {
          log.debug("Scheduling connection close after writeQueue flush")
          handle.key.writeQueue += PerformCleanClose
        } else close(handle, reason)
      }
    }

    def close(handle: Handle, reason: ConnectionClosedReason) {
      val key = handle.key.selectionKey
      if (key.isValid) {
        log.debug("Closing connection due to {}", reason)
        key.cancel()
        key.channel.close()
        handle.handler ! Closed(handle, reason)
        connectionsClosed += 1
      }
    }

    def connect(cmd: Connect, sender: ActorRef) {
      if (sender != null) {
        val channel = SocketChannel.open()
        configure(channel)
        cmd.localAddress.foreach(channel.socket().bind(_))
        if (settings.TcpReceiveBufferSize != 0)
          channel.socket.setReceiveBufferSize(settings.TcpReceiveBufferSize.toInt)
        if (channel.connect(cmd.remoteAddress)) {
          log.debug("Connection immediately established to {}", cmd.remoteAddress)
          val key = channel.register(selector, 0) // we don't enable any ops until we have a handle
          val localAddress = channel.socket.getLocalSocketAddress.asInstanceOf[InetSocketAddress]
          sender ! Connected(Key(key), cmd.remoteAddress, localAddress, cmd.tag)
        } else {
          val key = channel.register(selector, OP_CONNECT)
          key.attach((cmd, sender))
          log.debug("Connection request registered")
        }
      } else log.error("Cannot execute Connect command from unknown sender")
    }

    def bind(cmd: Bind, sender: ActorRef) {
      val channel = ServerSocketChannel.open
      channel.configureBlocking(false)
      if (settings.TcpReceiveBufferSize != 0)
        channel.socket.setReceiveBufferSize(settings.TcpReceiveBufferSize.toInt)
      channel.socket.bind(cmd.address, cmd.backlog)
      val key = channel.register(selector, OP_ACCEPT)
      key.attach(cmd)
      if (sender != null) sender ! Bound(Key(key), cmd.tag)
    }

    def unbind(cmd: Unbind, sender: ActorRef) {
      val key = cmd.bindingKey.selectionKey
      key.cancel()
      key.channel.close()
      if (sender != null) sender ! Unbound(cmd.bindingKey)
    }

    def deliverStats(sender: ActorRef) {
      val stats = Stats(System.currentTimeMillis - startTime, bytesRead, bytesWritten, connectionsOpened,
        connectionsClosed, commandsExecuted)
      if (sender != null) sender ! stats
    }

    def closeSelector() {
      import collection.JavaConverters._
      try {
        selector.keys.asScala.foreach(_.channel.close())
        selector.close()
      } catch {
        case NonFatal(e) =>
          log.error(e, "Error closing selector (key)")
      }
    }
  }

}

object IOBridge {
  private val lock = new AnyRef
  private var _runningBridges = Seq.empty[IOBridge]

  def runningBridges: Seq[IOBridge] =
    lock.synchronized(_runningBridges)

  def stopAll() {
    runningBridges.foreach(_.stop())
  }

  case class Stats(
    uptime: Long,
    bytesRead: Long,
    bytesWritten: Long,
    connectionsOpened: Long,
    connectionsClosed: Long,
    commandsExecuted: Long
  )

  // these two things can be elements of a Keys writeQueue (in addition to a raw ByteBuffer)
  private[io] case class Ack(receiver: ActorRef, msg: Any)
  private[io] case object PerformCleanClose

  ////////////// COMMANDS //////////////

  private[IOBridge] case class Stop(latch: CountDownLatch) extends Command

  //# public-commands
  // general commands not on the connection-level
  case class Bind(handleCreator: ActorRef,
                  address: InetSocketAddress,
                  backlog: Int,
                  tag: Any = ()) extends Command
  case class Unbind(bindingKey: Key) extends Command
  case class Connect(remoteAddress: InetSocketAddress,
                     localAddress: Option[InetSocketAddress] = None,
                     tag: Any = ()) extends Command
  object Connect {
    def apply(host: String, port: Int): Connect = apply(host, port, ())
    def apply(host: String, port: Int, tag: Any): Connect =
      Connect(new InetSocketAddress(host, port), None, tag)
  }
  case object GetStats extends Command

  // connection-level commands
  trait ConnectionCommand extends Command {
    def handle: Handle
  }
  case class Register(handle: Handle) extends ConnectionCommand
  case class Close(handle: Handle,
                   reason: ConnectionClosedReason) extends ConnectionCommand
  case class Send(handle: Handle,
                  buffers: Seq[ByteBuffer],
                  ack: Option[Any] = None) extends ConnectionCommand
  object Send {
    def apply(handle: Handle, buffer: ByteBuffer): Send =
      apply(handle, buffer, None)
    def apply(handle: Handle, buffer: ByteBuffer, ack: Option[Any]): Send =
      new Send(handle, buffer :: Nil, ack)
  }
  case class StopReading(handle: Handle) extends ConnectionCommand
  case class ResumeReading(handle: Handle) extends ConnectionCommand
  //#

  ////////////// EVENTS //////////////

  //# public-events
  // "general" events not on the connection-level
  case class Bound(bindingKey: Key, tag: Any) extends Event
  case class Unbound(bindingKey: Key) extends Event
  case class Connected(key: Key,
                       remoteAddress: InetSocketAddress,
                       localAddress: InetSocketAddress,
                       tag: Any) extends Event

  // connection-level events
  case class Closed(handle: Handle,
                    reason: ConnectionClosedReason) extends Event with IOClosed
  case class Received(handle: Handle, buffer: ByteBuffer) extends Event
  //#
}