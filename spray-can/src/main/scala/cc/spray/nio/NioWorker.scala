package cc.spray.nio

import java.nio.channels.spi.SelectorProvider
import java.nio.channels.{SelectionKey, SocketChannel, ServerSocketChannel}
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import annotation.tailrec
import collection.mutable.ListBuffer
import akka.actor.ActorRef

class NioWorker(config: NioWorkerConfig = NioWorkerConfig()) {
  private val _thread = new NioThread(config)

  def thread: Thread = _thread

  def start() {
    _thread.start()
  }

  def ! (cmd: Command) {
    _thread.post(cmd)
  }

  private class NioThread(config: NioWorkerConfig) extends Thread {
    import SelectionKey._

    val log = LoggerFactory.getLogger(getClass)
    val commandQueue = new SingleReaderConcurrentQueue[Command]
    val selector = SelectorProvider.provider.openSelector
    var stopCmd: Option[Stop] = None

    // executed from other threads!
    def post(cmd: Command) {
      commandQueue.enqueue(cmd)
      selector.wakeup()
    }

    override def run() {
      while(stopCmd.isEmpty) {
        if (commandQueue.isEmpty) {
          select()
        } else {
          runCommand(commandQueue.dequeue())
        }
      }
      safeSend(stopCmd.get.sender, Stopped)
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
          channel.write(buffers.head)
          if (buffers.head.remaining == 0) {  // if we were able to write the whole buffer
            buffers.remove(0)
            writeToChannel(buffers)           // we continue with the next buffer
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
      } catch { case e =>
        log.warn("Write error: closing connection due to {}", e)
        close(handle, Some(e))
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
          safeSend(handle.handler, Received(handle, buffer))
        } else {
          // if the peer shut down the socket cleanly, we do the same
          close(handle)
        }
      } catch { case e =>
        log.warn("Read error: closing connection due to {}", e)
        close(handle, Some(e))
      }
    }

    def accept(key: SelectionKey) {
      try {
        val socketChannel = key.channel.asInstanceOf[ServerSocketChannel].accept()
        socketChannel.configureBlocking(false)
        val connectionKey = socketChannel.register(selector, SelectionKey.OP_READ)
        val cmd = key.attachment.asInstanceOf[Bind]
        val handle = cmd.handleFactory(Key(connectionKey))
        log.debug("New connection accepted and registered")
        safeSend(handle.handler, Connected(handle))
      } catch { case e =>
        log.error("Accept error: could not accept new connection", e)
      }
    }

    def connect(key: SelectionKey) {
      try {
        key.channel.asInstanceOf[SocketChannel].finishConnect()
        key.interestOps(OP_READ)
        val cmd = key.attachment.asInstanceOf[Connect]
        connectionEstablished(key, cmd)
      } catch { case e =>
        log.error("Connect error: could not establish new connection", e)
      }
    }

    def runCommand(command: Command) {
      try {
        log.debug("Executing command {}", command)
        command match {
          // ConnectionCommands
          case x: Send => send(x)
          case x: Close => close(x.handle)

          // SuperCommands
          case x: Connect => connect(x)
          case x: Bind => bind(x)
          case x: Unbind => unbind(x)
          case x: Stop => stopCmd = Some(x)
        }
      } catch { case e =>
        log.error("Error during execution of command '" + command + "'", e)
        val receiver =  command match {
          case x: SuperCommand => x.sender
          case x: ConnectionCommand => x.handle.handler
        }
        safeSend(receiver, CommandError(command, e))
      }
    }

    def send(cmd: Send) {
      val key = cmd.handle.key
      key.writeBuffers += ListBuffer(cmd.buffers: _*)
      key.enable(OP_WRITE)
    }

    def close(handle: Handle, reason: Option[Throwable] = None) {
      val key = handle.key.selectionKey
      key.cancel()
      key.channel.close()
      safeSend(handle.handler, Closed(handle, reason))
    }

    def connect(cmd: Connect) {
      val channel = SocketChannel.open()
      channel.configureBlocking(false)
      if (channel.connect(cmd.address)) {
        val key = channel.register(selector, OP_READ)
        connectionEstablished(key, cmd)
      } else {
        val key = channel.register(selector, OP_CONNECT)
        key.attach(cmd)
        log.debug("Connection request registered")
      }
    }

    def connectionEstablished(key: SelectionKey, cmd: Connect) {
      val handle = cmd.handleFactory(Key(key))
      key.attach(handle)
      log.debug("Connection established to {}", cmd.address)
      safeSend(handle.handler, Connected(handle))
    }

    def bind(cmd: Bind) {
      val channel = ServerSocketChannel.open
      channel.configureBlocking(false)
      channel.socket.bind(cmd.address, cmd.backlog)
      val key = channel.register(selector, OP_ACCEPT)
      key.attach(cmd)
      safeSend(cmd.sender, Bound(Key(key)))
    }

    def unbind(cmd: Unbind) {
      val key = cmd.bindingKey.selectionKey
      key.cancel()
      key.channel.close()
      safeSend(cmd.sender, Unbound(cmd.bindingKey))
    }

    def safeSend(receiver: ActorRef, message: Any) {
      try {
        receiver! message
      } catch {
        case e => LoggerFactory.getLogger(getClass).error("Could not send '" + message + "' to '" + receiver + "'", e)
      }
    }
  }
}

object NioWorker extends NioWorker(NioWorkerConfig())