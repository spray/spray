/**
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */

package akka.io

import java.net.InetSocketAddress
import java.net.Socket
import akka.io.Inet._
import com.typesafe.config.Config
import scala.concurrent.duration._
import scala.collection.immutable
import akka.util.ByteString
import akka.actor._

/**
 * TCP Extension for Akka’s IO layer.
 *
 * <b>All contents of the `akka.io` package is marked “experimental”.</b>
 *
 * This marker signifies that APIs may still change in response to user feedback
 * through-out the 2.2 release cycle. The implementation itself is considered
 * stable and ready for production use.
 *
 * For a full description of the design and philosophy behind this IO
 * implementation please refer to <a href="http://doc.akka.io/">the Akka online documentation</a>.
 *
 * In order to open an outbound connection send a [[akka.io.Tcp.Connect]] message
 * to the [[akka.io.TcpExt#manager]].
 *
 * In order to start listening for inbound connetions send a [[akka.io.Tcp.Bind]]
 * message to the [[akka.io.TcpExt#manager]].
 *
 * The Java API for generating TCP commands is available at [[akka.io.TcpMessage]].
 */
object Tcp extends ExtensionKey[TcpExt] {

  /**
   * Java API: retrieve Tcp extension for the given system.
   */
  override def get(system: ActorSystem): TcpExt = super.get(system)

  /**
   * Scala API: this object contains all applicable socket options for TCP.
   *
   * For the Java API see [[akka.io.TcpSO]].
   */
  object SO extends Inet.SoForwarders {

    // general socket options

    /**
     * [[akka.io.Inet.SocketOption]] to enable or disable SO_KEEPALIVE
     *
     * For more information see [[java.net.Socket.setKeepAlive]]
     */
    case class KeepAlive(on: Boolean) extends SocketOption {
      override def afterConnect(s: Socket): Unit = s.setKeepAlive(on)
    }

    /**
     * [[akka.io.Inet.SocketOption]] to enable or disable OOBINLINE (receipt
     * of TCP urgent data) By default, this option is disabled and TCP urgent
     * data is silently discarded.
     *
     * For more information see [[java.net.Socket.setOOBInline]]
     */
    case class OOBInline(on: Boolean) extends SocketOption {
      override def afterConnect(s: Socket): Unit = s.setOOBInline(on)
    }

    // SO_LINGER is handled by the Close code

    /**
     * [[akka.io.Inet.SocketOption]] to enable or disable TCP_NODELAY
     * (disable or enable Nagle's algorithm)
     *
     * Please note, that TCP_NODELAY is enabled by default.
     *
     * For more information see [[java.net.Socket.setTcpNoDelay]]
     */
    case class TcpNoDelay(on: Boolean) extends SocketOption {
      override def afterConnect(s: Socket): Unit = s.setTcpNoDelay(on)
    }

  }

  /**
   * The common interface for [[akka.io.Command]] and [[akka.io.Tcp.Event]].
   */
  sealed trait Message

  /// COMMANDS

  /**
   * This is the common trait for all commands understood by TCP actors.
   */
  trait Command extends Message with SelectionHandler.HasFailureMessage {
    def failureMessage = CommandFailed(this)
  }

  /**
   * The Connect message is sent to the TCP manager actor, which is obtained via
   * [[akka.io.Tcp.TcpExt#manager]]. Either the manager replies with a [[akka.io.Tcp.CommandFailed]]
   * or the actor handling the new connection replies with a [[akka.io.Tcp.Connected]]
   * message.
   *
   * @param remoteAddress is the address to connect to
   * @param localAddress optionally specifies a specific address to bind to
   * @param options Please refer to the [[akka.io.Tcp.SO]] object for a list of all supported options.
   */
  case class Connect(remoteAddress: InetSocketAddress,
                     localAddress: Option[InetSocketAddress] = None,
                     options: immutable.Traversable[SocketOption] = Nil,
                     timeout: Option[FiniteDuration] = None) extends Command

  /**
   * The Bind message is send to the TCP manager actor, which is obtained via
   * [[akka.io.Tcp.TcpExt#manager]] in order to bind to a listening socket. The manager
   * replies either with a [[akka.io.Tcp.CommandFailed]] or the actor handling the listen
   * socket replies with a [[akka.io.Tcp.Bound]] message. If the local port is set to 0 in
   * the Bind message, then the [[akka.io.Tcp.Bound]] message should be inspected to find
   * the actual port which was bound to.
   *
   * @param handler The actor which will receive all incoming connection requests
   *                in the form of [[akka.io.Tcp.Connected]] messages.
   *
   * @param localAddress The socket address to bind to; use port zero for
   *                automatic assignment (i.e. an ephemeral port, see [[akka.io.Tcp.Bound]])
   *
   * @param backlog This specifies the number of unaccepted connections the O/S
   *                kernel will hold for this port before refusing connections.
   *
   * @param options Please refer to the [[akka.io.Tcp.SO]] object for a list of all supported options.
   */
  case class Bind(handler: ActorRef,
                  localAddress: InetSocketAddress,
                  backlog: Int = 100,
                  options: immutable.Traversable[SocketOption] = Nil) extends Command

  /**
   * This message must be sent to a TCP connection actor after receiving the
   * [[akka.io.Tcp.Connected]] message. The connection will not read any data from the
   * socket until this message is received, because this message defines the
   * actor which will receive all inbound data.
   *
   * @param handler The actor which will receive all incoming data and which
   *                will be informed when the connection is closed.
   *
   * @param keepOpenOnPeerClosed If this is set to true then the connection
   *                is not automatically closed when the peer closes its half,
   *                requiring an explicit [[akka.io.Tcp.Closed]] from our side when finished.
   *
   * @param useResumeWriting If this is set to true then the connection actor
   *                will refuse all further writes after issuing a [[akka.io.Tcp.CommandFailed]]
   *                notification until [[akka.io.Tcp.ResumeWriting]] is received. This can
   *                be used to implement NACK-based write backpressure.
   */
  case class Register(handler: ActorRef, keepOpenOnPeerClosed: Boolean = false, useResumeWriting: Boolean = true) extends Command

  /**
   * In order to close down a listening socket, send this message to that socket’s
   * actor (that is the actor which previously had sent the [[akka.io.Tcp.Bound]] message). The
   * listener socket actor will reply with a [[akka.io.Tcp.Unbound]] message.
   */
  case object Unbind extends Command

  /**
   * Common interface for all commands which aim to close down an open connection.
   */
  sealed trait CloseCommand extends Command {
    /**
     * The corresponding event which is sent as an acknowledgment once the
     * close operation is finished.
     */
    def event: ConnectionClosed
  }

  /**
   * A normal close operation will first flush pending writes and then close the
   * socket. The sender of this command and the registered handler for incoming
   * data will both be notified once the socket is closed using a [[akka.io.Tcp.Closed]]
   * message.
   */
  case object Close extends CloseCommand {
    /**
     * The corresponding event which is sent as an acknowledgment once the
     * close operation is finished.
     */
    override def event = Closed
  }

  /**
   * A confirmed close operation will flush pending writes and half-close the
   * connection, waiting for the peer to close the other half. The sender of this
   * command and the registered handler for incoming data will both be notified
   * once the socket is closed using a [[akka.io.Tcp.ConfirmedClosed]] message.
   */
  case object ConfirmedClose extends CloseCommand {
    /**
     * The corresponding event which is sent as an acknowledgment once the
     * close operation is finished.
     */
    override def event = ConfirmedClosed
  }

  /**
   * An abort operation will not flush pending writes and will issue a TCP ABORT
   * command to the O/S kernel which should result in a TCP_RST packet being sent
   * to the peer. The sender of this command and the registered handler for
   * incoming data will both be notified once the socket is closed using a
   * [[akka.io.Tcp.Aborted]] message.
   */
  case object Abort extends CloseCommand {
    /**
     * The corresponding event which is sent as an acknowledgment once the
     * close operation is finished.
     */
    override def event = Aborted
  }

  /**
   * Each [[akka.io.Tcp.WriteCommand]] can optionally request a positive acknowledgment to be sent
   * to the commanding actor. If such notification is not desired the [[akka.io.Tcp.WriteCommand#ack]]
   * must be set to an instance of this class. The token contained within can be used
   * to recognize which write failed when receiving a [[akka.io.Tcp.CommandFailed]] message.
   */
  case class NoAck(token: Any) extends Event

  /**
   * Default [[akka.io.Tcp.NoAck]] instance which is used when no acknowledgment information is
   * explicitly provided. Its “token” is `null`.
   */
  object NoAck extends NoAck(null)

  /**
   * Common interface for all write commands, currently [[akka.io.Tcp.Write]], [[akka.io.Tcp.WriteFile]]
   * and [[akka.io.Tcp.CompoundWrite]].
   */
  sealed abstract class WriteCommand extends Command {
    /**
     * Prepends this command with another `Write` or `WriteFile` to form
     * a `CompoundWrite`.
     */
    def +:(other: SimpleWriteCommand): CompoundWrite = CompoundWrite(other, this)

    /**
     * Prepends this command with a number of other writes.
     * The first element of the given Iterable becomes the first sub write of a potentially
     * created `CompoundWrite`.
     */
    def ++:(writes: Iterable[WriteCommand]): WriteCommand =
      writes.foldRight(this) {
        case (a: SimpleWriteCommand, b) ⇒ a +: b
        case (a: CompoundWrite, b)      ⇒ a ++: b
      }
  }

  object WriteCommand {
    /**
     * Combines the given number of write commands into one atomic `WriteCommand`.
     */
    def apply(writes: Iterable[WriteCommand]): WriteCommand = writes ++: Write.empty
  }

  /**
   * Common supertype of [[akka.io.Tcp.Write]] and [[akka.io.Tcp.WriteFile]].
   */
  sealed abstract class SimpleWriteCommand extends WriteCommand {
    require(ack != null, "ack must be non-null. Use NoAck if you don't want acks.")

    /**
     * The acknowledgment token associated with this write command.
     */
    def ack: Event

    /**
     * An acknowledgment is only sent if this write command “wants an ack”, which is
     * equivalent to the `ack` token not being a of type [[akka.io.Tcp.NoAck]].
     */
    def wantsAck: Boolean = !ack.isInstanceOf[NoAck]
  }

  /**
   * Write data to the TCP connection. If no ack is needed use the special
   * `NoAck` object. The connection actor will reply with a [[akka.io.Tcp.CommandFailed]]
   * message if the write could not be enqueued. If [[akka.io.Tcp.WriteCommand#wantsAck]]
   * returns true, the connection actor will reply with the supplied [[akka.io.Tcp.WriteCommand#ack]]
   * token once the write has been successfully enqueued to the O/S kernel.
   * <b>Note that this does not in any way guarantee that the data will be
   * or have been sent!</b> Unfortunately there is no way to determine whether
   * a particular write has been sent by the O/S.
   */
  case class Write(data: ByteString, ack: Event) extends SimpleWriteCommand
  object Write {
    /**
     * The empty Write doesn't write anything and isn't acknowledged.
     * It will, however, be denied and sent back with `CommandFailed` if the
     * connection isn't currently ready to send any data (because another WriteCommand
     * is still pending).
     */
    val empty: Write = Write(ByteString.empty, NoAck)

    /**
     * Create a new unacknowledged Write command with the given data.
     */
    def apply(data: ByteString): Write =
      if (data.isEmpty) empty else Write(data, NoAck)
  }

  /**
   * Write `count` bytes starting at `position` from file at `filePath` to the connection.
   * The count must be > 0. The connection actor will reply with a [[akka.io.Tcp.CommandFailed]]
   * message if the write could not be enqueued. If [[akka.io.Tcp.WriteCommand#wantsAck]]
   * returns true, the connection actor will reply with the supplied [[akka.io.Tcp.WriteCommand#ack]]
   * token once the write has been successfully enqueued to the O/S kernel.
   * <b>Note that this does not in any way guarantee that the data will be
   * or have been sent!</b> Unfortunately there is no way to determine whether
   * a particular write has been sent by the O/S.
   */
  case class WriteFile(filePath: String, position: Long, count: Long, ack: Event) extends SimpleWriteCommand {
    require(position >= 0, "WriteFile.position must be >= 0")
    require(count > 0, "WriteFile.count must be > 0")
  }

  /**
   * A write command which aggregates two other write commands. Using this construct
   * you can chain a number of [[akka.io.Tcp.Write]] and/or [[akka.io.Tcp.WriteFile]] commands together in a way
   * that allows them to be handled as a single write which gets written out to the
   * network as quickly as possible.
   * If the sub commands contain `ack` requests they will be honored as soon as the
   * respective write has been written completely.
   */
  case class CompoundWrite(override val head: SimpleWriteCommand, tailCommand: WriteCommand) extends WriteCommand
      with immutable.Iterable[SimpleWriteCommand] {

    def iterator: Iterator[SimpleWriteCommand] =
      new Iterator[SimpleWriteCommand] {
        private[this] var current: WriteCommand = CompoundWrite.this
        def hasNext: Boolean = current ne null
        def next(): SimpleWriteCommand =
          current match {
            case null                  ⇒ Iterator.empty.next()
            case CompoundWrite(h, t)   ⇒ { current = t; h }
            case x: SimpleWriteCommand ⇒ current = null; x
          }
      }
  }

  /**
   * When `useResumeWriting` is in effect as was indicated in the [[akka.io.Tcp.Register]] message
   * then this command needs to be sent to the connection actor in order to re-enable
   * writing after a [[akka.io.Tcp.CommandFailed]] event. All [[akka.io.Tcp.WriteCommand]] processed by the
   * connection actor between the first [[akka.io.Tcp.CommandFailed]] and subsequent reception of
   * this message will also be rejected with [[akka.io.Tcp.CommandFailed]].
   */
  case object ResumeWriting extends Command

  /**
   * Sending this command to the connection actor will disable reading from the TCP
   * socket. TCP flow-control will then propagate backpressure to the sender side
   * as buffers fill up on either end. To re-enable reading send [[akka.io.Tcp.ResumeReading]].
   */
  case object SuspendReading extends Command

  /**
   * This command needs to be sent to the connection actor after a [[akka.io.Tcp.SuspendReading]]
   * command in order to resume reading from the socket.
   */
  case object ResumeReading extends Command

  /// EVENTS
  /**
   * Common interface for all events generated by the TCP layer actors.
   */
  trait Event extends Message

  /**
   * Whenever data are read from a socket they will be transferred within this
   * class to the handler actor which was designated in the [[akka.io.Tcp.Register]] message.
   */
  case class Received(data: ByteString) extends Event

  /**
   * The connection actor sends this message either to the sender of a [[akka.io.Tcp.Connect]]
   * command (for outbound) or to the handler for incoming connections designated
   * in the [[akka.io.Tcp.Bind]] message. The connection is characterized by the `remoteAddress`
   * and `localAddress` TCP endpoints.
   */
  case class Connected(remoteAddress: InetSocketAddress, localAddress: InetSocketAddress) extends Event

  /**
   * Whenever a command cannot be completed, the queried actor will reply with
   * this message, wrapping the original command which failed.
   */
  case class CommandFailed(cmd: Command) extends Event

  /**
   * When `useResumeWriting` is in effect as indicated in the [[akka.io.Tcp.Register]] message,
   * the [[akka.io.Tcp.ResumeWriting]] command will be acknowledged by this message type, upon
   * which it is safe to send at least one write. This means that all writes preceding
   * the first [[akka.io.Tcp.CommandFailed]] message have been enqueued to the O/S kernel at this
   * point.
   */
  sealed trait WritingResumed extends Event
  case object WritingResumed extends WritingResumed

  /**
   * The sender of a [[akka.io.Tcp.Bind]] command will—in case of success—receive confirmation
   * in this form. If the bind address indicated a 0 port number, then the contained
   * `localAddress` can be used to find out which port was automatically assigned.
   */
  case class Bound(localAddress: InetSocketAddress) extends Event

  /**
   * The sender of an [[akka.io.Tcp.Unbind]] command will receive confirmation through this
   * message once the listening socket has been closed.
   */
  sealed trait Unbound extends Event
  case object Unbound extends Unbound

  /**
   * This is the common interface for all events which indicate that a connection
   * has been closed or half-closed.
   */
  sealed trait ConnectionClosed extends Event {
    /**
     * `true` iff the connection has been closed in response to an [[akka.io.Tcp.Abort]] command.
     */
    def isAborted: Boolean = false
    /**
     * `true` iff the connection has been fully closed in response to a
     * [[akka.io.Tcp.ConfirmedClose]] command.
     */
    def isConfirmed: Boolean = false
    /**
     * `true` iff the connection has been closed by the peer; in case
     * `keepOpenOnPeerClosed` is in effect as per the [[akka.io.Tcp.Register]] command,
     * this connection’s reading half is now closed.
     */
    def isPeerClosed: Boolean = false
    /**
     * `true` iff the connection has been closed due to an IO error.
     */
    def isErrorClosed: Boolean = false
    /**
     * If `isErrorClosed` returns true, then the error condition can be
     * retrieved by this method.
     */
    def getErrorCause: String = null
  }
  /**
   * The connection has been closed normally in response to a [[akka.io.Tcp.Close]] command.
   */
  case object Closed extends ConnectionClosed
  /**
   * The connection has been aborted in response to an [[akka.io.Tcp.Abort]] command.
   */
  case object Aborted extends ConnectionClosed {
    override def isAborted = true
  }
  /**
   * The connection has been half-closed by us and then half-close by the peer
   * in response to a [[akka.io.Tcp.ConfirmedClose]] command.
   */
  case object ConfirmedClosed extends ConnectionClosed {
    override def isConfirmed = true
  }
  /**
   * The peer has closed its writing half of the connection.
   */
  case object PeerClosed extends ConnectionClosed {
    override def isPeerClosed = true
  }
  /**
   * The connection has been closed due to an IO error.
   */
  case class ErrorClosed(cause: String) extends ConnectionClosed {
    override def isErrorClosed = true
    override def getErrorCause = cause
  }
}

class TcpExt(system: ExtendedActorSystem) extends IO.Extension {

  val Settings = new Settings(system.settings.config.getConfig("akka.io.tcp"))
  class Settings private[TcpExt] (_config: Config) extends SelectionHandlerSettings(_config) {
    import _config._

    val NrOfSelectors: Int = getInt("nr-of-selectors")
    val BatchAcceptLimit: Int = getInt("batch-accept-limit")
    val DirectBufferSize: Int = getIntBytes("direct-buffer-size")
    val MaxDirectBufferPoolSize: Int = getInt("direct-buffer-pool-limit")
    val RegisterTimeout: Duration = getString("register-timeout") match {
      case "infinite" ⇒ Duration.Undefined
      case x          ⇒ Duration(getMilliseconds("register-timeout"), MILLISECONDS)
    }
    val ReceivedMessageSizeLimit: Int = getString("max-received-message-size") match {
      case "unlimited" ⇒ Int.MaxValue
      case x           ⇒ getIntBytes("received-message-size-limit")
    }
    val ManagementDispatcher: String = getString("management-dispatcher")
    val FileIODispatcher: String = getString("file-io-dispatcher")
    val TransferToLimit: Int = getString("file-io-transferTo-limit") match {
      case "unlimited" ⇒ Int.MaxValue
      case _           ⇒ getIntBytes("file-io-transferTo-limit")
    }

    val MaxChannelsPerSelector: Int = if (MaxChannels == -1) -1 else math.max(MaxChannels / NrOfSelectors, 1)
    val FinishConnectRetries: Int = getInt("finish-connect-retries")

    require(NrOfSelectors > 0, "nr-of-selectors must be > 0")
    require(BatchAcceptLimit > 0, "batch-accept-limit must be > 0")
    require(FinishConnectRetries > 0, "finish-connect-retries must be > 0")

    private[this] def getIntBytes(path: String): Int = {
      val size = getBytes(path)
      require(size < Int.MaxValue, s"$path must be < 2 GiB")
      require(size >= 0, s"$path must be non-negative")
      size.toInt
    }
  }

  val manager: ActorRef = {
    system.asInstanceOf[ActorSystemImpl].systemActorOf(
      props = Props(new TcpManager(this)).withDispatcher(Settings.ManagementDispatcher),
      name = "IO-TCP")
  }

  /**
   * Java API: retrieve a reference to the manager actor.
   */
  def getManager: ActorRef = manager

  val bufferPool: BufferPool = new DirectByteBufferPool(Settings.DirectBufferSize, Settings.MaxDirectBufferPoolSize)
  val fileIoDispatcher = system.dispatchers.lookup(Settings.FileIODispatcher)
}

/**
 * Java API for accessing socket options.
 */
object TcpSO extends SoJavaFactories {
  import Tcp.SO._

  /**
   * [[akka.io.Inet.SocketOption]] to enable or disable SO_KEEPALIVE
   *
   * For more information see [[java.net.Socket.setKeepAlive]]
   */
  def keepAlive(on: Boolean) = KeepAlive(on)

  /**
   * [[akka.io.Inet.SocketOption]] to enable or disable OOBINLINE (receipt
   * of TCP urgent data) By default, this option is disabled and TCP urgent
   * data is silently discarded.
   *
   * For more information see [[java.net.Socket.setOOBInline]]
   */
  def oobInline(on: Boolean) = OOBInline(on)

  /**
   * [[akka.io.Inet.SocketOption]] to enable or disable TCP_NODELAY
   * (disable or enable Nagle's algorithm)
   *
   * Please note, that TCP_NODELAY is enabled by default.
   *
   * For more information see [[java.net.Socket.setTcpNoDelay]]
   */
  def tcpNoDelay(on: Boolean) = TcpNoDelay(on)
}