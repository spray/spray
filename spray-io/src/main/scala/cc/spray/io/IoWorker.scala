/*
 * Copyright (C) 2011, 2012 Mathias Doenitz
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

package cc.spray.io

import java.nio.channels.spi.SelectorProvider
import java.nio.ByteBuffer
import annotation.tailrec
import collection.mutable.ListBuffer
import java.nio.channels.{CancelledKeyException, SelectionKey, SocketChannel, ServerSocketChannel}
import java.util.concurrent.CountDownLatch
import akka.event.{LoggingBus, BusLogging, LoggingAdapter}
import com.typesafe.config.{ConfigFactory, Config}
import akka.actor.{Status, ActorSystem, ActorRef}
import java.net.{InetSocketAddress, SocketAddress}

// threadsafe
class IoWorker(log: LoggingAdapter, config: Config) {
  def this(loggingBus: LoggingBus, config: Config) =
    this(new BusLogging(loggingBus, "IoWorker", classOf[IoWorker]), config)
  def this(loggingSystem: ActorSystem, config: Config) =
    this(loggingSystem.eventStream, config)
  def this(loggingSystem: ActorSystem) = this(loggingSystem, ConfigFactory.load())
  def this(loggingBus: LoggingBus) = this(loggingBus, ConfigFactory.load())
  def this(log: LoggingAdapter) = this(log, ConfigFactory.load())

  import IoWorker._

  val settings = new IoWorkerSettings(config)
  private[this] var ioThread: Option[IoThread] = None

  /**
   * @return the IO thread if started and not yet stopped, otherwise None
   */
  def thread: Option[Thread] = lock.synchronized(ioThread)

  /**
   * Starts the IoWorker if not yet started
   * @return this instance
   */
  def start(): this.type = {
    lock.synchronized {
      if (ioThread.isEmpty) {
        ioThread = Some(new IoThread(settings, log))
        ioThread.get.start()
        _runningWorkers = _runningWorkers :+ this
      }
    }
    this
  }

  /**
   * Stops the IoWorker if not yet stopped.
   * The method blocks until the IoWorker has been successfully terminated.
   * It can be restarted by calling start().
   */
  def stop() {
    lock.synchronized {
      if (ioThread.isDefined) {
        val latch = new CountDownLatch(1)
        this ! Stop(latch)
        latch.await()
        ioThread = None
        _runningWorkers = _runningWorkers.filter(_ != this)
      }
    }
  }

  /**
   * Posts a Command to the IoWorkers command queue.
   */
  def ! (cmd: Command)(implicit sender: ActorRef = null) {
    if (ioThread.isEmpty)
      throw new IllegalStateException("Cannot post message to unstarted IoWorker")
    ioThread.get.post(cmd, sender)
  }

  /**
   * Posts a Command to the IoWorkers command queue.
   */
  def tell(cmd: Command, sender: ActorRef) {
    this.!(cmd)(sender)
  }

  private class IoThread(settings: IoWorkerSettings, log: LoggingAdapter) extends Thread {
    import SelectionKey._

    private val commandQueue = new SingleReaderConcurrentQueue[(Command, ActorRef)]
    private val selector = SelectorProvider.provider.openSelector
    private var stopped: Option[CountDownLatch] = None

    // stats fields
    private var startTime = 0L
    private var bytesRead = 0L
    private var bytesWritten = 0L
    private var connectionsOpened = 0L
    private var connectionsClosed = 0L
    private var commandsExecuted = 0L

    setName(settings.ThreadName + '-' + _runningWorkers.size)

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
      log.info("IoWorker thread '{}' started", Thread.currentThread.getName)
      while (stopped.isEmpty) {
        if (commandQueue.isEmpty) {
          select()
        } else {
          val cmdInstance = commandQueue.dequeue()
          runCommand(cmdInstance._1, cmdInstance._2)
          commandsExecuted += 1
        }
      }
      closeSelector()
      log.info("IoWorker thread '{}' stopped", Thread.currentThread.getName)
      stopped.get.countDown()
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

      @tailrec
      // returns true if the given buffers were completely written
      def writeToChannel(buffers: ListBuffer[ByteBuffer]): Boolean = {
        if (!buffers.isEmpty) {
          bytesWritten += channel.write(buffers.head)
          if (buffers.head.remaining == 0) {
            // if we were able to write the whole buffer
            buffers.remove(0)
            writeToChannel(buffers) // we continue with the next buffer
          } else false // otherwise we cannot drop the head and need to continue with it next time
        } else true
      }

      try {
        val buffers = handle.key.writeBuffers
        if (writeToChannel(buffers.head)) {
          if (buffers.remove(0) ne CleanCloseToken) {
            if (settings.ConfirmSends) handle.handler ! SendCompleted(handle)
            if (buffers.isEmpty) handle.key.disable(OP_WRITE)
            log.debug("Wrote {} bytes", bytesWritten - oldBytesWritten)
          } else close(handle, CleanClose)
        } else log.debug("Wrote {} bytes, more pending", bytesWritten - oldBytesWritten)
      } catch {
        case e =>
          log.warning("Write error: closing connection due to {}", e.toString)
          close(handle, IoError(e))
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
        case e =>
          log.warning("Read error: closing connection due to {}", e.toString)
          close(handle, IoError(e))
      }
    }

    def accept(key: SelectionKey) {
      try {
        val socketChannel = key.channel.asInstanceOf[ServerSocketChannel].accept()
        configure(socketChannel)
        val connectionKey = socketChannel.register(selector, 0) // we don't enable any ops until we have a handle
        val cmd = key.attachment.asInstanceOf[Bind]
        log.debug("New connection accepted on {}", cmd.address)
        cmd.handleCreator ! Connected(Key(connectionKey), cmd.address)
      } catch {
        case e => log.error(e, "Accept error: could not accept new connection")
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
      try {
        key.channel.asInstanceOf[SocketChannel].finishConnect()
        key.interestOps(0) // we don't enable any ops until we have a handle
        val cmdAndSender = key.attachment.asInstanceOf[(Connect, ActorRef)]
        val address = cmdAndSender._1.address
        log.debug("Connection established to {}", address)
        cmdAndSender._2 ! Connected(Key(key), address)
      } catch {
        case e => log.error(e, "Connect error: could not establish new connection")
      }
    }

    def runCommand(command: Command, sender: ActorRef) {
      try {
        log.debug("Executing command {}", command)
        command match {
          // ConnectionCommands
          case x: Send => send(x)
          case x: StopReading => x.handle.key.disable(OP_READ)
          case x: ResumeReading => x.handle.key.enable(OP_READ)
          case x: Register => register(x)
          case x: Close => close(x.handle, x.reason)

          // SuperCommands
          case x: Connect => connect(x, sender)
          case x: Bind => bind(x, sender)
          case x: Unbind => unbind(x, sender)
          case GetStats => deliverStats(sender)
          case Stop(latch) => stopped = Some(latch)

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

    def send(cmd: Send) {
      val key = cmd.handle.key
      key.writeBuffers += ListBuffer(cmd.buffers: _*)
      key.enable(OP_WRITE)
    }

    def register(cmd: Register) {
      val key = cmd.handle.key
      key.selectionKey.attach(cmd.handle)
      key.enable(OP_READ) // always start out in reading mode
      connectionsOpened += 1
    }

    def close(handle: Handle, reason: ConnectionClosedReason) {
      val key = handle.key.selectionKey
      if (key.isValid && reason == CleanClose && !handle.key.writeBuffers.isEmpty) {
        log.debug("Scheduling connection close after write buffers flush")
        handle.key.writeBuffers += CleanCloseToken
        handle.key.enable(OP_WRITE)
      } else {
        log.debug("Closing connection due to {}", reason)
        key.cancel()
        key.channel.close()
        handle.handler ! Closed(handle, reason)
        connectionsClosed += 1
      }
    }

    def connect(cmd: Connect, sender: ActorRef) {
      val channel = SocketChannel.open()
      configure(channel)
      if (settings.TcpReceiveBufferSize != 0)
        channel.socket.setReceiveBufferSize(settings.TcpReceiveBufferSize.toInt)
      if (channel.connect(cmd.address)) {
        log.debug("Connection immediately established to {}", cmd.address)
        val key = channel.register(selector, 0) // we don't enable any ops until we have a handle
        sender ! Connected(Key(key), cmd.address)
      } else {
        val key = channel.register(selector, OP_CONNECT)
        key.attach((cmd, sender))
        log.debug("Connection request registered")
      }
    }

    def bind(cmd: Bind, sender: ActorRef) {
      val channel = ServerSocketChannel.open
      channel.configureBlocking(false)
      if (settings.TcpReceiveBufferSize != 0)
        channel.socket.setReceiveBufferSize(settings.TcpReceiveBufferSize.toInt)
      channel.socket.bind(cmd.address, cmd.backlog)
      val key = channel.register(selector, OP_ACCEPT)
      key.attach(cmd)
      if (sender != null) sender ! Bound(Key(key))
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
        case e =>
          log.error(e, "Error closing selector (key)")
      }
    }
  }

}

object IoWorker {
  private val lock = new AnyRef
  private var _runningWorkers = Seq.empty[IoWorker]
  private val CleanCloseToken = ListBuffer.empty[ByteBuffer]

  def runningWorkers: Seq[IoWorker] =
    lock.synchronized(_runningWorkers)

  case class Stats(
    uptime: Long,
    bytesRead: Long,
    bytesWritten: Long,
    connectionsOpened: Long,
    connectionsClosed: Long,
    commandsExecuted: Long
  )

  ////////////// COMMANDS //////////////

  // "super" commands not on the connection-level
  private[IoWorker] case class Stop(latch: CountDownLatch) extends Command
  case class Bind(handleCreator: ActorRef, address: InetSocketAddress, backlog: Int) extends Command
  case class Unbind(bindingKey: Key) extends Command
  case class Connect(address: InetSocketAddress) extends Command
  case object GetStats extends Command

  // commands on the connection-level
  trait ConnectionCommand extends Command {
    def handle: Handle
  }

  case class Register(handle: Handle) extends ConnectionCommand
  case class Close(handle: Handle, reason: ConnectionClosedReason) extends ConnectionCommand
  case class Send(handle: Handle, buffers: Seq[ByteBuffer]) extends ConnectionCommand
  object Send {
    def apply(handle: Handle, buffer: ByteBuffer): Send = Send(handle, Seq(buffer))
  }
  case class StopReading(handle: Handle) extends ConnectionCommand
  case class ResumeReading(handle: Handle) extends ConnectionCommand

  ////////////// EVENTS //////////////

  // "general" events not on the connection-level
  case class Bound(bindingKey: Key) extends Event
  case class Unbound(bindingKey: Key) extends Event
  case class Connected(key: Key, address: InetSocketAddress) extends Event

  // connection-level events
  case class Closed(handle: Handle, reason: ConnectionClosedReason) extends Event
  case class SendCompleted(handle: Handle) extends Event
  case class Received(handle: Handle, buffer: ByteBuffer) extends Event
}