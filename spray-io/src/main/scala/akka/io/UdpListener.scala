/**
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */
package akka.io

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey._
import scala.annotation.tailrec
import scala.util.control.NonFatal
import akka.actor.{ ActorLogging, Actor, ActorRef }
import akka.util.ByteString
import akka.io.SelectionHandler._
import akka.io.Udp._

/**
 * INTERNAL API
 */
private[io] class UdpListener(val udp: UdpExt,
                              channelRegistry: ChannelRegistry,
                              bindCommander: ActorRef,
                              bind: Bind)
    extends Actor with ActorLogging with WithUdpSend {

  import bind._
  import udp.bufferPool
  import udp.settings._

  context.watch(handler) // sign death pact
  val channel = {
    val datagramChannel = DatagramChannel.open
    datagramChannel.configureBlocking(false)
    val socket = datagramChannel.socket
    options.foreach(_.beforeDatagramBind(socket))
    try {
      socket.bind(endpoint)
      require(socket.getLocalSocketAddress.isInstanceOf[InetSocketAddress],
        s"bound to unknown SocketAddress [${socket.getLocalSocketAddress}]")
    } catch {
      case NonFatal(e) ⇒
        bindCommander ! CommandFailed(bind)
        log.error(e, "Failed to bind UDP channel to endpoint [{}]", endpoint)
        context.stop(self)
    }
    datagramChannel
  }
  channelRegistry.register(channel, OP_READ)
  log.debug("Successfully bound to [{}]", endpoint)

  def receive: Receive = {
    case registration: ChannelRegistration ⇒
      bindCommander ! Bound(channel.socket.getLocalSocketAddress.asInstanceOf[InetSocketAddress])
      context.become(readHandlers(registration) orElse sendHandlers(registration), discardOld = true)
  }

  def readHandlers(registration: ChannelRegistration): Receive = {
    case StopReading     ⇒ registration.disableInterest(OP_READ)
    case ResumeReading   ⇒ registration.enableInterest(OP_READ)
    case ChannelReadable ⇒ doReceive(handler, registration)

    case Unbind ⇒
      log.debug("Unbinding endpoint [{}]", endpoint)
      try {
        channel.close()
        sender ! Unbound
        log.debug("Unbound endpoint [{}], stopping listener", endpoint)
      } finally context.stop(self)
  }

  def doReceive(handler: ActorRef, registration: ChannelRegistration): Unit = {
    @tailrec def innerReceive(readsLeft: Int, buffer: ByteBuffer) {
      buffer.clear()
      buffer.limit(DirectBufferSize)

      channel.receive(buffer) match {
        case sender: InetSocketAddress ⇒
          buffer.flip()
          handler ! Received(ByteString(buffer), sender)
          if (readsLeft > 0) innerReceive(readsLeft - 1, buffer)
        case null ⇒ // null means no data was available
      }
    }

    val buffer = bufferPool.acquire()
    try innerReceive(BatchReceiveLimit, buffer) finally {
      bufferPool.release(buffer)
      registration.enableInterest(OP_READ)
    }
  }

  override def postStop() {
    if (channel.isOpen) {
      log.debug("Closing DatagramChannel after being stopped")
      try channel.close()
      catch {
        case NonFatal(e) ⇒ log.error(e, "Error closing DatagramChannel")
      }
    }
  }
}
