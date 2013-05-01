/**
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */

package akka.io

import java.nio.channels.{ SocketChannel, SelectionKey, ServerSocketChannel }
import scala.annotation.tailrec
import scala.util.control.NonFatal
import akka.actor.{ Props, ActorLogging, ActorRef, Actor }
import akka.io.SelectionHandler._
import akka.io.Tcp._
import akka.io.IO.HasFailureMessage
import java.net.InetSocketAddress

/**
 * INTERNAL API
 */
private[io] object TcpListener {

  case class RegisterIncoming(channel: SocketChannel) extends HasFailureMessage {
    def failureMessage = FailedRegisterIncoming(channel)
  }

  case class FailedRegisterIncoming(channel: SocketChannel)

}

/**
 * INTERNAL API
 */
private[io] class TcpListener(selectorRouter: ActorRef,
                              tcp: TcpExt,
                              channelRegistry: ChannelRegistry,
                              bindCommander: ActorRef,
                              bind: Bind) extends Actor with ActorLogging {
  import TcpListener._
  import tcp.Settings._
  import bind._

  context.watch(handler) // sign death pact
  val channel = {
    val serverSocketChannel = ServerSocketChannel.open
    serverSocketChannel.configureBlocking(false)
    val socket = serverSocketChannel.socket
    options.foreach(_.beforeServerSocketBind(socket))
    try {
      socket.bind(localAddress, backlog)
      require(socket.getLocalSocketAddress.isInstanceOf[InetSocketAddress],
        s"bound to unknown SocketAddress [${socket.getLocalSocketAddress}]")
    } catch {
      case NonFatal(e) ⇒
        bindCommander ! bind.failureMessage
        log.error(e, "Bind failed for TCP channel on endpoint [{}]", localAddress)
        context.stop(self)
    }
    serverSocketChannel
  }
  channelRegistry.register(channel, SelectionKey.OP_ACCEPT)
  log.debug("Successfully bound to {}", localAddress)

  def receive: Receive = {
    case registration: ChannelRegistration ⇒
      bindCommander ! Bound(channel.socket.getLocalSocketAddress.asInstanceOf[InetSocketAddress])
      context.become(bound(registration))
  }

  def bound(registration: ChannelRegistration): Receive = {
    case ChannelAcceptable ⇒
      acceptAllPending(registration, BatchAcceptLimit)

    case FailedRegisterIncoming(socketChannel) ⇒
      log.warning("Could not register incoming connection since selector capacity limit is reached, closing connection")
      try socketChannel.close()
      catch {
        case NonFatal(e) ⇒ log.error(e, "Error closing channel")
      }

    case Unbind ⇒
      log.debug("Unbinding endpoint {}", localAddress)
      channel.close()
      sender ! Unbound
      log.debug("Unbound endpoint {}, stopping listener", localAddress)
      context.stop(self)
  }

  @tailrec final def acceptAllPending(registration: ChannelRegistration, limit: Int): Unit = {
    val socketChannel =
      if (limit > 0) {
        try channel.accept()
        catch {
          case NonFatal(e) ⇒ { log.error(e, "Accept error: could not accept new connection due to {}", e); null }
        }
      } else null
    if (socketChannel != null) {
      log.debug("New connection accepted")
      socketChannel.configureBlocking(false)
      def props(registry: ChannelRegistry) = Props(new TcpIncomingConnection(tcp, socketChannel, registry, handler, options))
      selectorRouter ! WorkerForCommand(RegisterIncoming(socketChannel), self, props)
      acceptAllPending(registration, limit - 1)
    } else registration.enableInterest(SelectionKey.OP_ACCEPT)
  }

  override def postStop() {
    try {
      if (channel.isOpen) {
        log.debug("Closing serverSocketChannel after being stopped")
        channel.close()
      }
    } catch {
      case NonFatal(e) ⇒ log.error(e, "Error closing ServerSocketChannel")
    }
  }

}
