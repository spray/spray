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
import java.nio.channels.{SelectionKey, SocketChannel, ServerSocketChannel}
import java.nio.channels.spi.SelectorProvider
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import org.slf4j.LoggerFactory
import annotation.tailrec
import collection.mutable.ListBuffer
import akka.actor.ActorRef

class IoWorker(config: IoWorkerConfig) {
  nioWorker =>
  private val log = LoggerFactory.getLogger(getClass)
  private lazy val _thread = new IoThread(config)

  def thread: Thread = _thread

  def start() {
    _thread.start()
  }

  def !(msg: Any)(implicit sender: UntypedChannel) {
    msg match {
      case cmd: Command => _thread.post(cmd)
      case x => log.warn("Received non-command message '{}', ignoring...", x)
    }
  }

  private class IoThread(config: IoWorkerConfig) extends Thread {

    import SelectionKey._

    private val commandQueue = new SingleReaderConcurrentQueue[Command]
    private val selector = SelectorProvider.provider.openSelector
    private var stopped: Option[Stop] = None

    // stats fields
    private var startTime = 0L
    private var bytesRead = 0L
    private var bytesWritten = 0L
    private var connectionsOpened = 0L
    private var connectionsClosed = 0L
    private var commandsExecuted = 0L

    setName(config.threadName + '-' + IoWorker.counter.incrementAndGet())
    setDaemon(true)

    override def start() {
      if (getState == Thread.State.NEW) {
        startTime = System.currentTimeMillis
        super.start()
      }
    }

    // executed from other threads!
    def post(cmd: Command) {
      commandQueue.enqueue(cmd)
      selector.wakeup()
    }

    override def run() {
      while (stopped.isEmpty) {
        if (commandQueue.isEmpty) {
          select()
        } else {
          runCommand(commandQueue.dequeue())
          commandsExecuted += 1
        }
      }
      closeSelector()
      for (stopCmd <- stopped; stopAck <- stopCmd.ackTo) {
        safeSend(stopAck, Stopped)
      }
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
        } else log.warn("Invalid selection key: {}", key)
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
          safeSend(handle.handler, CompletedSend(handle))
          if (buffers.isEmpty) handle.key.disable(OP_WRITE)
        }
      } catch {
        case e =>
          log.warn("Write error: closing connection due to {}", e.toString)
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
          safeSend(handle.handler, Received(handle, buffer))
        } else {
          // if the peer shut down the socket cleanly, we do the same
          close(handle, PeerClosed)
        }
      } catch {
        case e =>
          log.warn("Read error: closing connection due to {}", e.toString)
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
        safeSend(cmd.handleCreator, Connected(Key(connectionKey)))
      } catch {
        case e =>
          log.error("Accept error: could not accept new connection", e)
      }
    }

    def connect(key: SelectionKey) {
      try {
        key.channel.asInstanceOf[SocketChannel].finishConnect()
        key.interestOps(0) // we don't enable any ops until we have a handle
        val cmd = key.attachment.asInstanceOf[Connect]
        log.debug("Connection established to {}", cmd.address)
        safeSend(cmd.handleCreator, Connected(Key(key)))
      } catch {
        case e =>
          log.error("Connect error: could not establish new connection", e)
      }
    }

    def runCommand(command: Command) {
      try {
        log.debug("Executing command {}", command)
        command match {
          // ConnectionCommands
          case x: Send => send(x)
          case x: Register => register(x)
          case x: Close => close(x.handle, x.reason)

          // SuperCommands
          case x: Connect => connect(x)
          case x: Bind => bind(x)
          case x: Unbind => unbind(x)
          case x: GetStats => deliverStats(x)
          case x: Stop => stopped = Some(x)
        }
      } catch {
        case e =>
          log.error("Error during execution of command '" + command + "'", e)
          for (receiver <- command.errorReceiver) {
            safeSend(receiver, CommandError(command, e))
          }
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
      safeSend(handle.handler, Closed(handle, reason))
      connectionsClosed += 1
    }

    def connect(cmd: Connect) {
      val channel = SocketChannel.open()
      channel.configureBlocking(false)
      if (channel.connect(cmd.address)) {
        log.debug("Connection immediately established to {}", cmd.address)
        val key = channel.register(selector, 0) // we don't enable any ops until we have a handle
        safeSend(cmd.handleCreator, Connected(Key(key)))
      } else {
        val key = channel.register(selector, OP_CONNECT)
        key.attach(cmd)
        log.debug("Connection request registered")
      }
    }

    def bind(cmd: Bind) {
      val channel = ServerSocketChannel.open
      channel.configureBlocking(false)
      channel.socket.bind(cmd.address, cmd.backlog)
      val key = channel.register(selector, OP_ACCEPT)
      key.attach(cmd)
      cmd.ackTo foreach (safeSend(_, Bound(Key(key))))
    }

    def unbind(cmd: Unbind) {
      val key = cmd.bindingKey.selectionKey
      key.cancel()
      key.channel.close()
      cmd.ackTo foreach (safeSend(_, Unbound(cmd.bindingKey)))
    }

    def deliverStats(cmd: GetStats) {
      val stats = NioWorkerStats(System.currentTimeMillis - startTime, bytesRead, bytesWritten, connectionsOpened,
        connectionsClosed, commandsExecuted, commandQueue.size)
      safeSend(cmd.deliverTo, stats)
    }

    def safeSend(receiver: ActorRef, message: Any) {
      try {
        receiver.!(message)(nioWorker)
      } catch {
        case e => LoggerFactory.getLogger(getClass).error("Could not send '" + message + "' to '" + receiver + "'", e)
      }
    }

    def closeSelector() {
      try {
        selector.close()
      } catch {
        case e =>
          log.error("Error closing selector", e)
      }
    }
  }

}

object IoWorker {
  private val counter = new AtomicInteger
}