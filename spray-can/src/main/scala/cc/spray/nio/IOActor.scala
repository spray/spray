package cc.spray.nio

import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.nio.channels.spi.SelectorProvider
import java.util.concurrent.TimeUnit
import annotation.tailrec
import cc.spray.can._
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.channels.{CancelledKeyException, ServerSocketChannel, SocketChannel, SelectionKey}
import akka.actor._
import collection.mutable.Queue

sealed trait ConnectionHandle

private[nio] class ConnectionRecord(
  val key: SelectionKey,
  val connectionActor: ActorRef
) extends ConnectionHandle {
  // elements are either a ByteBuffer or a message of any type to be sent back to the connectionActor
  var pendingWrites = Queue.empty[Any]

  private var _ops = key.interestOps
  def setOps(ops: Int) { key.interestOps { _ops = ops; ops } }
  def addOps(ops: Int) { key.interestOps { _ops |= ops; _ops } }
  def removeOps(ops: Int) { key.interestOps { _ops &= ~ops; _ops } }
}

// incoming messages
case class Connect(address: InetSocketAddress)
case class Close(handle: ConnectionHandle)
case class Write(handle: ConnectionHandle, buffers: Seq[ByteBuffer], completionMessage: Any)

// outgoing messages
case class Received(buffer: ByteBuffer)
case class Closed(cause: Option[Exception])

object IOActor {
  private[nio] val Select = new AnyRef
}

class IOActor(connectionActorFactory: ConnectionHandle => ActorRef,
              config: IOConfig) extends Actor with SelectorWakingDispatcherComponent {
  import IOActor._

  private lazy val log = LoggerFactory.getLogger(getClass)
  private val readBuffer = ByteBuffer.allocateDirect(config.readBufferSize)
  private val selector = SelectorProvider.provider.openSelector

  private lazy val serverSocketChannel = config.serverAddress.flatMap(startServer)

  //protected val connections = new LinkedList[Conn] // a list of all connections registered on the selector

  // statistics
  private var startTime: Long = _
  private var requestsDispatched: Long = _
  private var requestsTimedOut: Long = _

  // we use our own custom single-thread dispatcher, because our thread will, for the most time,
  // be blocked at selector selection, therefore we need to wake it up upon message or task arrival
  if (!self.isBeingRestarted) {
    self.dispatcher = new SelectorWakingDispatcher(config.threadName)
  }

  override def preStart() {
    // CAUTION: as of Akka 2.0 this method will not be called during a restart
    if (config.serverAddress.isDefined) {
      serverSocketChannel // trigger serverSocketChannel initialization and registration
    } else {
      log.info("Starting spray-nio client")
    }
    startTime = System.currentTimeMillis
    self ! Select // start the selection loop
  }

  override def preRestart(reason: Throwable, message: Option[Any]) {
    log.error("Crashed, about to restart...\nmessage: {}\nreason: {}", message.getOrElse("None"), reason)
    cleanUp()
  }

  override def postStop() {
    cleanUp()
  }

  private def startServer(address: InetSocketAddress): Option[ServerSocketChannel] = {
    log.info("Starting spray-nio server on {}", address)
    try {
      val channel = ServerSocketChannel.open
      channel.configureBlocking(false)
      channel.socket.bind(address)
      channel.register(selector, SelectionKey.OP_ACCEPT)
      Some(channel)
    } catch {
      case e: IOException => {
        log.error("Could not open and register server socket on {}:\n{}", config.endpoint, e.toString)
        self ! PoisonPill
        None
      }
    }
  }

  protected def receive = {
    case Select => select()
    case GetStats => self.reply(stats)
  }

  private def select() {
    // The following select() call only really blocks for a longer period of time if the actors mailbox is empty and no
    // other tasks have been scheduled by the dispatcher. Otherwise the dispatcher will either already have called
    // selector.wakeup() (which causes the following call to not block at all) or do so in a short while.
    selector.select()
    val keys = selector.selectedKeys.iterator
    while (keys.hasNext) {
      val key = keys.next
      keys.remove()
      if (key.isValid) {
        if (key.isWritable) write(key)
        else if (key.isReadable) read(key)
        else if (key.isAcceptable) accept(key)
        else if (key.isConnectable) connect(key)
      } else log.warn("Invalid selection key: {}", key)
    }
    self ! Select // loop
  }

  private def write(key: SelectionKey) {
    log.debug("Writing to connection")
    val conn = key.attachment.asInstanceOf[ConnectionRecord]
    val channel = key.channel.asInstanceOf[SocketChannel]

    @tailrec
    def writeToChannel() {
      if (!conn.pendingWrites.isEmpty) {
        val recurse = conn.pendingWrites.head match {
          case buffer: ByteBuffer =>
            channel.write(buffer)
            // recurse if we were able to write the whole buffer
            // otherwise we cannot drop the head and need to continue with it next time
            buffer.remaining == 0
          case msg =>
            log.debug("Completed write cycle, informing connection actor with {}", msg)
            conn.connectionActor ! msg
            true
        }
        if (recurse) {
          conn.pendingWrites.dequeue()
          writeToChannel()
        }
      } else conn.removeOps(SelectionKey.OP_WRITE) // nothing more to write, disable writing
    }

    try {
      writeToChannel()
      connections.refresh(conn)
      finishWrite(conn)
    } catch {
      case e: IOException => {
        log.warn("Write error: closing connection due to {}", e)
        cleanUp(conn)
        conn.connectionActor ! Closed(Some(e))
      }
    }
  }

  protected def stats = {
    log.debug("Received GetStats request, responding with stats")
    Stats(System.currentTimeMillis - startTime, requestsDispatched, requestsTimedOut, openRequestCount, connections.size)
  }

}