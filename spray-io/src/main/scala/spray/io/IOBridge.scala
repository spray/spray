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

import java.nio.ByteBuffer
import java.net.InetSocketAddress
import java.nio.channels._
import com.typesafe.config.Config
import scala.annotation.tailrec
import scala.util.control.{Exception, NonFatal}
import akka.spray.io.{IOBridgeDispatcherConfigurator, SelectorWakingMailbox}
import akka.spray.UnregisteredActorRef
import akka.event.Logging
import akka.actor._
import spray.util._
import SelectionKey._


final class IOBridge private[io](settings: IOBridge.Settings, isRoot: Boolean = true)
  extends Actor with SprayActorLogging {
  import IOBridge._

  private[this] val mailbox: SelectorWakingMailbox = IOExtension.myMailbox.getOrElse(
    sys.error("Cannot create %s with a dispatcher other than %s".format(getClass, classOf[IOBridgeDispatcherConfigurator]))
  )
  import mailbox.selector
  private[this] val debug = TaggableLog(log, Logging.DebugLevel)
  private[this] val warning = TaggableLog(log, Logging.WarningLevel)
  private[this] val error = TaggableLog(log, Logging.ErrorLevel)
  private[this] var cachedBuffer: ByteBuffer = _
  private[this] val subBridgesEnabled = isRoot && settings.Parallelism > 1
  private[this] val subBridges: Array[ActorRef] =
    if (subBridgesEnabled) {
      Array.tabulate(settings.Parallelism) { ix =>
        context.actorOf(
          props = Props(new IOBridge(settings, isRoot = false)).withDispatcher(DispatcherName),
          name = "sub-" + ix
        )
      }
    } else Array.empty

  // stats fields
  private[this] val startTime = System.currentTimeMillis
  private[this] var bytesRead = 0L
  private[this] var bytesWritten = 0L
  private[this] var connectionsOpened = 0L
  private[this] var connectionsClosed = 0L
  private[this] var commandsExecuted = 0L

  override def preStart() {
    log.info("{} started", context.self.path)
  }

  override def postStop() {
    closeSelector()
    log.info("{} stopped", context.self.path)
  }

  def receive: Receive = handleCommand andThen { _ =>
    commandsExecuted += 1
    while (mailbox.isEmpty) select()
  }

  def handleCommand: Receive = {
    case cmd: ConnectionCommand =>
      try runCommand(cmd)
      catch handleConnectionCommandError(cmd)

    case cmd: Command =>
      try runCommand(cmd)
      catch handleCommandError(cmd)
  }

  def handleConnectionCommandError(cmd: ConnectionCommand): Exception.Catcher[Unit] = {
    case e: CancelledKeyException =>
      warning.log(cmd.connection.tag, "Could not execute command '{}': connection reset by peer", cmd)
      cmd.connection.handler ! Closed(cmd.connection, ConnectionCloseReasons.PeerClosed)

    case NonFatal(e) =>
      error.log(cmd.connection.tag, "Error during execution of command '{}': {}", cmd, e)
      sender ! Status.Failure(CommandException(cmd, e))
  }

  def handleCommandError(cmd: Command): Exception.Catcher[Unit] = {
    case NonFatal(e) =>
      log.error(e, "Error during execution of command '{}': {}", cmd, e)
      sender ! Status.Failure(CommandException(cmd, e))
  }

  def runCommand(cmd: ConnectionCommand) {
    if (cmd.connection.selectionKey.selector != selector) {
      warning.log(cmd.connection.tag, "Target socket of command {} is not handled by this IOBridge", cmd)
      unhandled(cmd)
    } else cmd match {
      case Send(connection, buffers, ack) => send(connection, buffers, ack)
      case StopReading(connection)        => connection.keyImpl.disable(OP_READ)
      case ResumeReading(connection)      => connection.keyImpl.enable(OP_READ)
      case Register(connection)           => register(connection)
      case Close(connection, reason)      => scheduleClose(connection, reason)
      case x => unhandled(x)
    }
  }

  def runCommand(cmd: Command) {
    cmd match {
      case cmd: Connect       if isRoot => connect(cmd)
      case cmd: Bind          if isRoot => bind(cmd)
      case Unbind(bindingKey) if isRoot => unbind(bindingKey)
      case GetStats                     => collectAndDispatchStats(sender)

      case AssignConnection(channel, connectedEventReceiver, tag) =>
        connectedEventReceiver ! registerConnectionAndCreateConnectedEvent(channel, tag)
      case Select => // ignore, this message just restarts the selection loop

      case x => unhandled(x)
    }
  }

  def send(connection: Connection, buffers: Seq[ByteBuffer], ack: Option[Any]) {
    if (debug.enabled)
      debug.log(connection.tag, "Scheduling {} bytes in {} buffers for writing (ack: {})", buffers.map(_.remaining).sum,
        buffers.size, ack)
    connection.writeQueue ++= buffers
    if (ack.isDefined) connection.writeQueue += Ack(sender, ack.get)
    connection.keyImpl.enable(OP_WRITE)
  }

  def register(connection: Connection) {
    debug.log(connection.tag, "Registering connection, enabling reads")
    connection.selectionKey.attach(connection)
    connection.keyImpl.enable(OP_READ) // always start out in reading mode
    connectionsOpened += 1
  }

  def scheduleClose(connection: Connection, reason: CloseCommandReason) {
    if (connection.selectionKey.isValid) {
      if (connection.writeQueue.isEmpty)
        if (reason == ConnectionCloseReasons.ConfirmedClose)
          sendFIN(connection)
        else
          close(connection, reason)
      else {
        debug.log(connection.tag, "Scheduling connection close after writeQueue flush")
        connection.writeQueue += reason
      }
    }
  }

  def sendFIN(connection: Connection) {
    debug.log(connection.tag, "Sending FIN")
    connection.channel.socket.shutdownOutput()
  }

  def close(connection: Connection, reason: ClosedEventReason) {
    val selectionKey = connection.selectionKey
    if (selectionKey.isValid) {
      debug.log(connection.tag, "Closing connection due to {}", reason)
      selectionKey.cancel()
      selectionKey.channel.close()
      connection.handler ! Closed(connection, reason)
      connectionsClosed += 1
    } else debug.log(connection.tag, "Tried to close connection with reason {} but selectionKey is already invalid", reason)
  }

  def connect(cmd: Connect) {
    debug.log(cmd.tag, "Executing {}", cmd)
    val channel = SocketChannel.open()
    configure(channel)
    cmd.localAddress.foreach(channel.socket().bind(_))
    if (settings.TcpReceiveBufferSize != 0) channel.socket.setReceiveBufferSize(settings.TcpReceiveBufferSize.toInt)
    if (channel.connect(cmd.remoteAddress)) {
      debug.log(cmd.tag, "Connection immediately established to {}", cmd.remoteAddress)
      val key = channel.register(selector, 0) // we don't enable any ops until we have a connection
      completeConnect(key, cmd, sender)
    } else {
      val key = channel.register(selector, OP_CONNECT)
      key.attach((cmd, sender))
      debug.log(cmd.tag, "Connection request registered")
    }
  }

  def bind(cmd: Bind) {
    debug.log(cmd.tag, "Executing {}", cmd)
    val channel = ServerSocketChannel.open
    channel.configureBlocking(false)
    if (settings.TcpReceiveBufferSize != 0) channel.socket.setReceiveBufferSize(settings.TcpReceiveBufferSize.toInt)
    channel.socket.bind(cmd.address, cmd.backlog)
    val key = channel.register(selector, OP_ACCEPT)
    key.attach((cmd, sender))
    sender ! Bound(new KeyImpl(self, key), cmd.tag)
  }

  def unbind(bindingKey: Key) {
    val keyImpl = bindingKey.asInstanceOf[KeyImpl].selectionKey
    val (bind: Bind, _) = keyImpl.attachment()
    debug.log(bind.tag, "Unbinding {}", bind.address)
    keyImpl.cancel()
    keyImpl.channel.close()
    sender ! Unbound(bindingKey, bind.tag)
  }

  def collectAndDispatchStats(resultReceiver: ActorRef) {
    log.debug("Collecting and dispatching stats to {}", resultReceiver)
    def dispatch(result: Map[ActorRef, Stats]): List[ActorRef] => Unit = {
      case Nil => resultReceiver ! StatsMap(result)
      case head :: tail =>
        val receiver = new UnregisteredActorRef(context) {
          def handle(message: Any)(implicit sender: ActorRef) {
            val StatsMap(map) = message
            dispatch(result ++ map)(tail)
          }
        }
        head.tell(GetStats, receiver)
    }
    val uptime = System.currentTimeMillis - startTime
    val stats = Stats(uptime, bytesRead, bytesWritten, connectionsOpened, connectionsClosed, commandsExecuted)
    dispatch(Map(self -> stats))(subBridges.toList)
  }

  def select() {
    // The following select() call blocks if there are no mailbox messages ("regular" or system) pending.
    // Otherwise the Mailbox will already have called `selector.wakeup()` (or will do so shortly),
    // which causes select() to not block at all (or for long).
    selector.select()
    val keys = selector.selectedKeys
    if (!keys.isEmpty) {
      val iterator = keys.iterator()
      while (iterator.hasNext) {
        val key = iterator.next
        if (key.isValid) {
          if (key.isWritable) write(key) // prefer writing over reading if both ops are ready
          else if (key.isReadable) read(key)
          else if (key.isAcceptable) accept(key)
          else if (key.isConnectable) connect(key)
        } else log.warning("Invalid selection key: {}", key)
      }
      keys.clear()
    }
  }

  def write(selectionKey: SelectionKey) {
    val connection = selectionKey.attachment.asInstanceOf[Connection]
    debug.log(connection.tag, "Writing to connection")
    val channel = selectionKey.channel.asInstanceOf[SocketChannel]
    val oldBytesWritten = bytesWritten
    val writeQueue = connection.writeQueue

    @tailrec
    def writeToChannel() {
      if (!writeQueue.isEmpty) {
        writeQueue.dequeue() match {
          case buf: ByteBuffer =>
            bytesWritten += channel.write(buf)
            if (buf.remaining == 0) writeToChannel() // we continue with the next buffer
            else {
              buf +=: writeQueue // we cannot drop the head and need to continue with it next time
              debug.log(connection.tag, "Wrote {} bytes, more pending", bytesWritten - oldBytesWritten)
            }
          case Ack(receiver, msg) =>
            receiver ! msg
            writeToChannel()
          case ConnectionCloseReasons.ConfirmedClose => sendFIN(connection)
          case reason: CloseCommandReason => close(connection, reason)
        }
      } else {
        connection.keyImpl.disable(OP_WRITE)
        debug.log(connection.tag, "Wrote {} bytes", bytesWritten - oldBytesWritten)
      }
    }

    try writeToChannel()
    catch {
      case NonFatal(e) =>
        warning.log(connection.tag, "Write error: closing connection due to {}", e)
        close(connection, ConnectionCloseReasons.IOError(e))
    }
  }

  def read(key: SelectionKey) {
    val connection = key.attachment.asInstanceOf[Connection]
    debug.log(connection.tag, "Reading from connection")
    val channel = key.channel.asInstanceOf[SocketChannel]
    val buffer = if (cachedBuffer != null) {
      val x = cachedBuffer; cachedBuffer = null; x
    } else ByteBuffer.allocate(settings.ReadBufferSize.toInt)

    try {
      if (channel.read(buffer) > -1) {
        buffer.flip()
        debug.log(connection.tag, "Read {} bytes", buffer.limit)
        bytesRead += buffer.limit
        connection.handler ! Received(connection, buffer)
      } else {
        cachedBuffer = buffer // the buffer was not used, so save it for the next read

        // if the peer shut down the socket cleanly, we do the same
        val reason =
          if (channel.socket.isOutputShutdown) ConnectionCloseReasons.ConfirmedClose
          else ConnectionCloseReasons.PeerClosed
        close(connection, reason)
      }
    } catch {
      case NonFatal(e) =>
        warning.log(connection.tag, "Read error: closing connection due to {}", e)
        close(connection, ConnectionCloseReasons.IOError(e))
    }
  }

  def accept(key: SelectionKey) {
    val (cmd, cmdSender) = key.attachment.asInstanceOf[(Bind, ActorRef)]
    try {
      val channel = key.channel.asInstanceOf[ServerSocketChannel].accept()
      debug.log(cmd.tag, "New connection accepted on {}", cmd.address)
      configure(channel)
      if (subBridgesEnabled)
        childFor(channel) ! AssignConnection(channel, cmdSender, cmd.tag)
      else
        cmdSender ! registerConnectionAndCreateConnectedEvent(channel, cmd.tag)
    } catch {
      case NonFatal(e) => error.log(cmd.tag, "Accept error: could not accept new connection due to {}", e)
    }
  }

  def connect(key: SelectionKey) {
    val (cmd, cmdSender) = key.attachment.asInstanceOf[(Connect, ActorRef)]
    try {
      key.channel.asInstanceOf[SocketChannel].finishConnect()
      debug.log(cmd.tag, "Connection established to {}", cmd.remoteAddress)
      completeConnect(key, cmd, cmdSender)
    } catch {
      case NonFatal(e) =>
        error.log(cmd.tag, "Connect error: could not establish new connection to {} due to {}", cmd.remoteAddress, e)
        cmdSender ! Status.Failure(CommandException(cmd, e))
    }
  }

  def completeConnect(key: SelectionKey, cmd: Connect, cmdSender: ActorRef) {
    val channel = key.channel.asInstanceOf[SocketChannel]
    if (subBridgesEnabled) {
      key.cancel() // unregister from our selector
      childFor(channel) ! AssignConnection(channel, cmdSender, cmd.tag)
    } else {
      key.interestOps(0) // we don't enable any ops until we have a connection
      cmdSender ! Connected(new KeyImpl(self, key), cmd.tag)
    }
  }

  def childFor(channel: SocketChannel) =
    subBridges(System.identityHashCode(channel) % subBridges.length)

  def registerConnectionAndCreateConnectedEvent(channel: SocketChannel, tag: Any) = {
    val key = channel.register(selector, 0) // we don't enable any ops until we have a connection
    Connected(new KeyImpl(self, key), tag)
  }

  def configure(channel: SocketChannel) {
    val socket = channel.socket
    // tcpReceiveBufferSize needs to be set on the ServerSocket before the bind, so we don't set it here
    if (settings.TcpSendBufferSize != 0) socket.setSendBufferSize(settings.TcpSendBufferSize.toInt)
    if (settings.TcpKeepAlive != 0)      socket.setKeepAlive(settings.TcpKeepAlive > 0)
    if (settings.TcpNoDelay != 0)        socket.setTcpNoDelay(settings.TcpNoDelay > 0)
    channel.configureBlocking(false)
  }

  def closeSelector() {
    import scala.collection.JavaConverters._
    try {
      selector.keys.asScala.foreach(_.channel.close())
      selector.close()
    } catch {
      case NonFatal(e) => log.error(e, "Error closing selector or key")
    }
  }
}

object IOBridge {
  private[io] val DispatcherName = "spray.io.io-bridge-dispatcher"

  class Settings(config: Config) {
    protected val c: Config = ConfigUtils.prepareSubConfig(config, "spray.io")

    val Parallelism          = c getInt     "parallelism"
    val ReadBufferSize       = c getBytes   "read-buffer-size"

    val TcpReceiveBufferSize = c getBytes   "tcp.receive-buffer-size"
    val TcpSendBufferSize    = c getBytes   "tcp.send-buffer-size"
    val TcpKeepAlive         = c getInt     "tcp.keep-alive"
    val TcpNoDelay           = c getInt     "tcp.no-delay"

    require(Parallelism          > 0,  "parallelism must be > 0")
    require(ReadBufferSize       > 0,  "read-buffer-size must be > 0")
    require(TcpReceiveBufferSize >= 0, "receive-buffer-size must be >= 0")
    require(TcpSendBufferSize    >= 0, "send-buffer-size must be >= 0")
  }

  //# connection-interface
  trait Connection {
    /**
     * The key identifying the connection.
     */
    def key: Key

    /**
     * The actor handling network events related to this connection.
     */
    def handler: ActorRef

    /**
     * A custom, application-defined tag object for this connection.
     * Currently it is used for connection-specific enabling/disabling
     * of encryption (see `SslTlsSupport.Enabling` trait)
     * or for custom log marking (see `LogMarking` trait).
     */
    def tag: Any

    /**
     * Determines whether this connections socket is currently connected or not.
     */
    def connected: Boolean = // ...
      socket.isConnected                                                                          // hide

    /**
     * The remote address this connection is attached to.
     */
    def remoteAddress: InetSocketAddress = // ...
      socket.getRemoteSocketAddress.asInstanceOf[InetSocketAddress]                               // hide

    /**
     * The local address this connection is attached to.
     */
    def localAddress: InetSocketAddress = // ...
      socket.getLocalSocketAddress.asInstanceOf[InetSocketAddress]                                // hide
                                                                                                  // hide
    ////////////////////// INTERNAL //////////////////////////                                    // hide
                                                                                                  // hide
    private[IOBridge] def socket = channel.socket                                                 // hide
    private[IOBridge] def channel = selectionKey.channel.asInstanceOf[SocketChannel]              // hide
    private[IOBridge] def selectionKey = keyImpl.selectionKey                                     // hide
    private[IOBridge] def keyImpl = key.asInstanceOf[KeyImpl]                                     // hide
                                                                                                  // hide
    // the writeQueue contains instances of either ByteBuffer, CloseCommandReason or Ack          // hide
    // we don't introduce a dedicated sum type for this since we want to save the extra           // hide
    // allocation that would be required for wrapping the ByteBuffers                             // hide
    private[IOBridge] val writeQueue = collection.mutable.Queue.empty[AnyRef]                     // hide
  }
  //#

  sealed trait Key {
    /**
     * The IOBridge owning this key.
     */
    def ioBridge: ActorRef
  }

  private class KeyImpl(val ioBridge: ActorRef, val selectionKey: SelectionKey) extends Key {
    private[this] var _ops = 0
    def enable(ops: Int) {
      if ((_ops & ops) == 0) {
        _ops |= ops
        selectionKey.interestOps(_ops)
      }
    }
    def disable(ops: Int) {
      if ((_ops & ops) != 0) {
        _ops &= ~ops
        selectionKey.interestOps(_ops)
      }
    }
  }

  private case class Ack(receiver: ActorRef, msg: Any)

  case class StatsMap(map: Map[ActorRef, Stats])

  case class Stats(
    uptime: Long,
    bytesRead: Long,
    bytesWritten: Long,
    connectionsOpened: Long,
    connectionsClosed: Long,
    commandsExecuted: Long
  )

  ////////////// COMMANDS //////////////

  //# public-commands
  // general commands not on the connection-level
  case class Bind(address: InetSocketAddress, backlog: Int, tag: Any) extends Command
  object Bind {
    def apply(interface: String, port: Int, bindingBacklog: Int = 100, tag: Any = ()): Bind =
      Bind(new InetSocketAddress(interface, port), bindingBacklog, tag)
  }
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
    def connection: Connection
  }
  case class Register(connection: Connection) extends ConnectionCommand
  case class Close(connection: Connection,
                   reason: CloseCommandReason) extends ConnectionCommand
  case class Send(connection: Connection,
                  buffers: Seq[ByteBuffer],
                  ack: Option[Any] = None) extends ConnectionCommand
  object Send {
    def apply(connection: Connection, buffer: ByteBuffer): Send =
      apply(connection, buffer, None)
    def apply(connection: Connection, buffer: ByteBuffer, ack: Option[Any]): Send =
      new Send(connection, buffer :: Nil, ack)
  }
  case class StopReading(connection: Connection) extends ConnectionCommand
  case class ResumeReading(connection: Connection) extends ConnectionCommand
  //#

  ////////////// EVENTS //////////////

  //# public-events
  // "general" events not on the connection-level
  case class Bound(bindingKey: Key, tag: Any) extends Event
  case class Unbound(bindingKey: Key, tag: Any) extends Event
  case class Connected(key: Key, tag: Any) extends Event

  // connection-level events
  case class Closed(connection: Connection, reason: ClosedEventReason) extends Event with IOClosed
  case class Received(connection: Connection, buffer: ByteBuffer) extends Event
  //#

  ///////////// INTERNAL COMMANDS ///////////////

  // TODO: mark private[akka] after migration
  case object Select extends Command

  private case class AssignConnection(channel: SocketChannel, connectedEventReceiver: ActorRef, tag: Any) extends Command
}