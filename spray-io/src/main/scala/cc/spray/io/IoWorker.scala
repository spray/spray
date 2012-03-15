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
import akka.actor.{ActorSystem, ActorRef}
import akka.event.{BusLogging, LoggingAdapter}
import java.util.concurrent.CountDownLatch
import java.net.{Socket, SocketAddress}

// threadsafe
class IoWorker(config: IoWorkerConfig = IoWorkerConfig()) {
  import IoWorker._

  private var ioThread: Option[IoThread] = None

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
        if (_system.isEmpty) _system = Some(ActorSystem("IoWorker"))
        ioThread = Some {
          new IoThread(
            config = config,
            name = config.threadName + '-' + _runningWorkers.size,
            log = new BusLogging(_system.get.eventStream, "IoWorker", getClass)
          )
        }
        _runningWorkers = _runningWorkers :+ this
        ioThread.get.start()
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
        if (_runningWorkers.isEmpty) {
          _system.get.shutdown()
          _system = None
        }
      }
    }
  }

  /**
   * Posts a Command to the IoWorkers command queue.
   */
  def ! (cmd: Command)(implicit sender: ActorRef = null) {
    if (ioThread.isEmpty)
      throw new IllegalStateException("Cannot post message to unstarted IoWorker")
    ioThread.get.post(cmd, if (sender != null) sender else _system.get.deadLetters)
  }

  private class IoThread(config: IoWorkerConfig, name: String, log: LoggingAdapter) extends Thread {
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

    setName(name)

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
          buffers.remove(0)
          handle.handler ! SendCompleted(handle)
          if (buffers.isEmpty) handle.key.disable(OP_WRITE)
        }
        log.debug("Wrote {} bytes", bytesWritten - oldBytesWritten)
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
      val buffer = ByteBuffer.allocate(config.readBufferSize)

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
        log.debug("New connection accepted")
        val cmd = key.attachment.asInstanceOf[Bind]
        cmd.handleCreator ! Connected(Key(connectionKey), ())
      } catch {
        case e => log.error(e, "Accept error: could not accept new connection")
      }
    }

    def configure(channel: SocketChannel) {
      val socket = channel.socket
      // tcpReceiveBufferSize needs to be set on the ServerSocket before the bind, so we don't set it here
      if (config.tcpSendBufferSize.isDefined) socket.setSendBufferSize(config.tcpSendBufferSize.get)
      if (config.tcpKeepAlive.isDefined)      socket.setKeepAlive(config.tcpKeepAlive.get)
      if (config.tcpNoDelay.isDefined)        socket.setTcpNoDelay(config.tcpNoDelay.get)
      channel.configureBlocking(false)
    }

    def connect(key: SelectionKey) {
      try {
        key.channel.asInstanceOf[SocketChannel].finishConnect()
        key.interestOps(0) // we don't enable any ops until we have a handle
        val cmd = key.attachment.asInstanceOf[Connect]
        log.debug("Connection established to {}", cmd.address)
        cmd.handleCreator ! Connected(Key(key), cmd.tag)
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
          case x: Register => register(x)
          case x: Close => close(x.handle, x.reason)

          // SuperCommands
          case x: Connect => connect(x)
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
          sender ! CommandError(command, e)
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
      key.cancel()
      key.channel.close()
      handle.handler ! Closed(handle, reason)
      connectionsClosed += 1
    }

    def connect(cmd: Connect) {
      val channel = SocketChannel.open()
      configure(channel)
      config.tcpReceiveBufferSize.foreach(channel.socket setReceiveBufferSize _)
      if (channel.connect(cmd.address)) {
        log.debug("Connection immediately established to {}", cmd.address)
        val key = channel.register(selector, 0) // we don't enable any ops until we have a handle
        cmd.handleCreator ! Connected(Key(key), cmd.tag)
      } else {
        val key = channel.register(selector, OP_CONNECT)
        key.attach(cmd)
        log.debug("Connection request registered")
      }
    }

    def bind(cmd: Bind, sender: ActorRef) {
      val channel = ServerSocketChannel.open
      channel.configureBlocking(false)
      config.tcpReceiveBufferSize.foreach(channel.socket setReceiveBufferSize _)
      channel.socket.bind(cmd.address, cmd.backlog)
      val key = channel.register(selector, OP_ACCEPT)
      key.attach(cmd)
      sender ! Bound(Key(key), cmd.tag)
    }

    def unbind(cmd: Unbind, sender: ActorRef) {
      val key = cmd.bindingKey.selectionKey
      key.cancel()
      key.channel.close()
      sender ! Unbound(cmd.bindingKey, cmd.tag)
    }

    def deliverStats(sender: ActorRef) {
      val stats = IoWorkerStats(System.currentTimeMillis - startTime, bytesRead, bytesWritten, connectionsOpened,
        connectionsClosed, commandsExecuted)
      sender ! stats
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
  private var _system: Option[ActorSystem] = None
  private var _runningWorkers = Seq.empty[IoWorker]

  def runningWorkers: Seq[IoWorker] =
    lock.synchronized(_runningWorkers)

  ////////////// COMMANDS //////////////

  // "super" commands not on the connection-level
  private[IoWorker] case class Stop(latch: CountDownLatch) extends Command
  case class Bind(handleCreator: ActorRef, address: SocketAddress, backlog: Int, tag: Any) extends Command
  case class Unbind(bindingKey: Key, tag: Any) extends Command
  case class Connect(handleCreator: ActorRef, address: SocketAddress, tag: Any) extends Command
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

  ////////////// EVENTS //////////////

  // "general" events not on the connection-level
  case class Bound(bindingKey: Key, tag: Any) extends Event
  case class Unbound(bindingKey: Key, tag: Any) extends Event
  case class Connected(key: Key, tag: Any) extends Event

  // connection-level events
  case class Closed(handle: Handle, reason: ConnectionClosedReason) extends Event
  case class SendCompleted(handle: Handle) extends Event
  case class Received(handle: Handle, buffer: ByteBuffer) extends Event
}