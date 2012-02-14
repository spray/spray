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

import config.IoWorkerConfig
import java.nio.channels.spi.SelectorProvider
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import annotation.tailrec
import collection.mutable.ListBuffer
import akka.actor.{Props, Actor, ActorLogging, ActorRef}
import java.nio.channels.{CancelledKeyException, SelectionKey, SocketChannel, ServerSocketChannel}

class IoWorker(config: IoWorkerConfig = IoWorkerConfig()) extends Actor with ActorLogging {
  private lazy val ioThread = new IoThread(config)

  def thread: Thread = ioThread

  override def preStart() {
    ioThread.start()
  }

  protected def receive = {
    case cmd: Command => ioThread.post(cmd, sender)
  }

  override def postStop() {
    ioThread.post(Stop, context.system.deadLetters)
  }

  private class IoThread(config: IoWorkerConfig)(implicit self: ActorRef) extends Thread {
    import SelectionKey._

    private val commandQueue = new SingleReaderConcurrentQueue[(Command, ActorRef)]
    private val selector = SelectorProvider.provider.openSelector
    private var stopped: Option[ActorRef] = None

    // stats fields
    private var startTime = 0L
    private var bytesRead = 0L
    private var bytesWritten = 0L
    private var connectionsOpened = 0L
    private var connectionsClosed = 0L
    private var commandsExecuted = 0L

    setName(config.threadName + '-' + IoWorker.counter.incrementAndGet())

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
      stopped.get ! Stopped
      log.info("IoWorker thread '{}' stopped", Thread.currentThread.getName)
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
      } catch {
        case e =>
          log.warning("Write error: closing connection due to {}", e.toString)
          close(handle, IoError(e))
      }
    }

    def read(key: SelectionKey) {
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
        socketChannel.configureBlocking(false)
        val connectionKey = socketChannel.register(selector, 0) // we don't enable any ops until we have a handle
        log.debug("New connection accepted")
        val cmd = key.attachment.asInstanceOf[Bind]
        cmd.handleCreator ! Connected(Key(connectionKey))
      } catch {
        case e => log.error(e, "Accept error: could not accept new connection")
      }
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
          case Stop => stopped = Some(sender)
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
      channel.configureBlocking(false)
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
      channel.socket.bind(cmd.address, cmd.backlog)
      val key = channel.register(selector, OP_ACCEPT)
      key.attach(cmd)
      sender ! Bound(Key(key))
    }

    def unbind(cmd: Unbind, sender: ActorRef) {
      val key = cmd.bindingKey.selectionKey
      key.cancel()
      key.channel.close()
      sender ! Unbound(cmd.bindingKey)
    }

    def deliverStats(sender: ActorRef) {
      val stats = IoWorkerStats(System.currentTimeMillis - startTime, bytesRead, bytesWritten, connectionsOpened,
        connectionsClosed, commandsExecuted, commandQueue.size)
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
  private val counter = new AtomicInteger
}

private[io] case class InitConnectionHandler(handlerProps: Props, selectionKey: SelectionKey)