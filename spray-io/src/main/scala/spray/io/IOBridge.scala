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
import java.nio.channels.{CancelledKeyException, SelectionKey, SocketChannel, ServerSocketChannel}
import java.net.InetSocketAddress
import com.typesafe.config.Config
import scala.annotation.tailrec
import akka.actor._
import akka.util.NonFatal
import akka.spray.io.{IOBridgeDispatcherConfigurator, SelectorWakingMailbox}
import akka.spray.UnregisteredActorRef
import spray.util._
import SelectionKey._


class IOBridge private[io](settings: IOBridge.Settings, isRoot: Boolean = true) extends Actor with ActorLogging {
  import IOBridge._

  private[this] val mailbox: SelectorWakingMailbox = IOExtension.myMailbox.getOrElse(
    sys.error("Cannot create %s with a dispatcher other than %s".format(getClass, classOf[IOBridgeDispatcherConfigurator]))
  )
  import mailbox.selector
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
    log.info("IOBridge '{}' started", context.self.path)
  }

  override def postStop() {
    closeSelector()
    log.info("IOBridge '{}' stopped", context.self.path)
  }

  def receive: Receive = { case command: Command =>
    try {
      log.debug("Executing command {}", command)
      runCommand(command)
    } catch {
      case e: CancelledKeyException if command.isInstanceOf[ConnectionCommand] =>
        log.warning("Could not execute command '{}': connection reset by peer", command)
        val handle = command.asInstanceOf[ConnectionCommand].handle
        handle.handler ! Closed(handle, ConnectionCloseReasons.PeerClosed)
      case NonFatal(e) =>
        log.error(e, "Error during execution of command '{}'", command)
        if (sender != null) sender ! Status.Failure(CommandException(command, e))
    }
    commandsExecuted += 1
    while (mailbox.isEmpty) select()
  }

  def runCommand(cmd: Command) {
    cmd match {
      case cc: ConnectionCommand =>
        if (cc.handle.key.selectionKey.selector != selector)
          sys.error("Target socket of this command is not handled by this IOBridge")
        cc match {
          case Send(handle, buffers, ack) => send(handle, buffers, ack)
          case StopReading(handle)        => handle.key.disable(OP_READ)
          case ResumeReading(handle)      => handle.key.enable(OP_READ)
          case Register(handle)           => register(handle)
          case Close(handle, reason)      => scheduleClose(handle, reason)
        }

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

  def send(handle: Handle, buffers: Seq[ByteBuffer], ack: Option[Any]) {
    val key = handle.key
    key.writeQueue ++= buffers
    if (ack.isDefined) key.writeQueue += Ack(sender, ack.get)
    key.enable(OP_WRITE)
  }

  def register(handle: Handle) {
    val key = handle.key
    key.selectionKey.attach(handle)
    key.enable(OP_READ) // always start out in reading mode
    connectionsOpened += 1
  }

  def scheduleClose(handle: Handle, reason: CloseCommandReason) {
    val key = handle.key.selectionKey
    if (key.isValid) {
      if (handle.key.writeQueue.isEmpty)
        if (reason == ConnectionCloseReasons.ConfirmedClose)
          sendFIN(handle.key.channel)
        else
          close(handle, reason)
      else {
        log.debug("Scheduling connection close after writeQueue flush")
        handle.key.writeQueue += reason
      }
    }
  }

  def sendFIN(channel: SocketChannel) {
    log.debug("Sending FIN")
    channel.socket.shutdownOutput()
  }

  def close(handle: Handle, reason: ClosedEventReason) {
    val key = handle.key.selectionKey
    if (key.isValid) {
      log.debug("Closing connection due to {}", reason)
      key.cancel()
      key.channel.close()
      handle.handler ! Closed(handle, reason)
      connectionsClosed += 1
    }
  }

  def connect(cmd: Connect) {
    val channel = SocketChannel.open()
    configure(channel)
    cmd.localAddress.foreach(channel.socket().bind(_))
    if (settings.TcpReceiveBufferSize != 0) channel.socket.setReceiveBufferSize(settings.TcpReceiveBufferSize.toInt)
    if (channel.connect(cmd.remoteAddress)) {
      log.debug("Connection immediately established to {}", cmd.remoteAddress)
      val key = channel.register(selector, 0) // we don't enable any ops until we have a handle
      completeConnect(key, cmd, sender)
    } else {
      val key = channel.register(selector, OP_CONNECT)
      key.attach((cmd, sender))
      log.debug("Connection request registered")
    }
  }

  def bind(cmd: Bind) {
    val channel = ServerSocketChannel.open
    channel.configureBlocking(false)
    if (settings.TcpReceiveBufferSize != 0) channel.socket.setReceiveBufferSize(settings.TcpReceiveBufferSize.toInt)
    channel.socket.bind(cmd.address, cmd.backlog)
    val key = channel.register(selector, OP_ACCEPT)
    key.attach(cmd)
    sender ! Bound(Key(key), cmd.tag)
  }

  def unbind(bindingKey: Key) {
    val key = bindingKey.selectionKey
    key.cancel()
    key.channel.close()
    sender ! Unbound(bindingKey)
  }

  def collectAndDispatchStats(resultReceiver: ActorRef) {
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
          case ConnectionCloseReasons.ConfirmedClose => sendFIN(channel)
          case reason: CloseCommandReason => close(handle, reason)
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
        close(handle, ConnectionCloseReasons.IOError(e))
    }
  }

  def read(key: SelectionKey) {
    log.debug("Reading from connection")
    val handle = key.attachment.asInstanceOf[Handle]
    val channel = key.channel.asInstanceOf[SocketChannel]
    val buffer = if (cachedBuffer != null) {
      val x = cachedBuffer; cachedBuffer = null; x
    } else ByteBuffer.allocate(settings.ReadBufferSize.toInt)

    try {
      if (channel.read(buffer) > -1) {
        buffer.flip()
        log.debug("Read {} bytes", buffer.limit)
        bytesRead += buffer.limit
        handle.handler ! Received(handle, buffer)
      } else {
        cachedBuffer = buffer // the buffer was not used, so save it for the next read

        // if the peer shut down the socket cleanly, we do the same
        val reason =
          if (channel.socket.isOutputShutdown) ConnectionCloseReasons.ConfirmedClose
          else ConnectionCloseReasons.PeerClosed
        close(handle, reason)
      }
    } catch {
      case NonFatal(e) =>
        log.warning("Read error: closing connection due to {}", e.toString)
        close(handle, ConnectionCloseReasons.IOError(e))
    }
  }

  def accept(key: SelectionKey) {
    try {
      val cmd = key.attachment.asInstanceOf[Bind]
      val channel = key.channel.asInstanceOf[ServerSocketChannel].accept()
      log.debug("New connection accepted on {}", cmd.address)
      configure(channel)
      if (subBridgesEnabled)
        childFor(channel) ! AssignConnection(channel, cmd.handleCreator, cmd.tag)
      else
        cmd.handleCreator ! registerConnectionAndCreateConnectedEvent(channel, cmd.tag)
    } catch {
      case NonFatal(e) => log.error(e, "Accept error: could not accept new connection")
    }
  }

  def connect(key: SelectionKey) {
    val (cmd, cmdSender) = key.attachment.asInstanceOf[(Connect, ActorRef)]
    try {
      key.channel.asInstanceOf[SocketChannel].finishConnect()
      log.debug("Connection established to {}", cmd.remoteAddress)
      completeConnect(key, cmd, cmdSender)
    } catch {
      case NonFatal(e) =>
        log.error(e, "Connect error: could not establish new connection to {}", cmd.remoteAddress)
        cmdSender ! Status.Failure(CommandException(cmd, e))
    }
  }

  def completeConnect(key: SelectionKey, cmd: Connect, cmdSender: ActorRef) {
    val channel = key.channel.asInstanceOf[SocketChannel]
    if (subBridgesEnabled) {
      key.cancel() // unregister from our selector
      childFor(channel) ! AssignConnection(channel, cmdSender, cmd.tag)
    } else {
      key.interestOps(0) // we don't enable any ops until we have a handle
      cmdSender ! connectedEvent(key, channel, cmd.tag)
    }
  }

  def childFor(channel: SocketChannel) =
    subBridges(System.identityHashCode(channel) % subBridges.length)

  def registerConnectionAndCreateConnectedEvent(channel: SocketChannel, tag: Any) = {
    val key = channel.register(selector, 0) // we don't enable any ops until we have a handle
    connectedEvent(key, channel, tag)
  }

  def connectedEvent(key: SelectionKey, channel: SocketChannel, tag: Any) = {
    val remoteAddress = channel.socket.getRemoteSocketAddress.asInstanceOf[InetSocketAddress]
    val localAddress = channel.socket.getLocalSocketAddress.asInstanceOf[InetSocketAddress]
    Connected(Key(key), remoteAddress, localAddress, tag)
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
      case NonFatal(e) => log.error(e, "Error closing selector (key)")
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
                   reason: CloseCommandReason) extends ConnectionCommand
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
                    reason: ClosedEventReason) extends Event with IOClosed
  case class Received(handle: Handle, buffer: ByteBuffer) extends Event
  //#

  ///////////// INTERNAL COMMANDS ///////////////

  // TODO: mark private[akka] after migration
  case object Select extends Command

  private case class AssignConnection(channel: SocketChannel, connectedEventReceiver: ActorRef, tag: Any) extends Command
}