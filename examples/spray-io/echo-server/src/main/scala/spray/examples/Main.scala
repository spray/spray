package spray.examples

import java.net.InetSocketAddress
import scala.collection.immutable.Queue
import scala.concurrent.duration._
import akka.actor.{Actor, Props, ActorSystem}
import akka.pattern.ask
import akka.io.{Tcp, IO}
import akka.util.{ByteString, Timeout}
import spray.util._


object Main extends App {
  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("echo-server")

  // and our actual server "service" actor
  val server = system.actorOf(Props(new EchoServer), name = "echo-server")

  // we bind the server to a port on localhost and hook
  // in a continuation that informs us when bound
  val endpoint = new InetSocketAddress("localhost", 23456)
  implicit val bindingTimeout = Timeout(1.second)
  import system.dispatcher // execution context for the future

  val boundFuture = IO(Tcp) ? Tcp.Bind(server, endpoint)

  boundFuture.onSuccess { case Tcp.Bound =>
    println("\nBound echo-server to " + endpoint)
    println("Run `telnet localhost 23456`, type something and press RETURN. Type `STOP` to exit...\n")
  }
}

class EchoServer extends Actor with SprayActorLogging {

  def receive = simpleEchoing

  def simpleEchoing: Receive = connectingAndClosing orElse {
    case Tcp.Received(data) =>
      data.utf8String.trim match {
        case "STOP" =>
          info("Shutting down")
          sender ! Tcp.Close

        case "ACKED" =>
          info("Switching to ACKed echoing mode")
          context.become(ackedEchoing)

        case x =>
          log.debug("Received '{}', echoing ...", x)
          sender ! Tcp.Write(data, ack = 'SentOk)
      }

    case 'SentOk =>
      log.debug("Send completed")
  }

  def ackedEchoing: Receive = connectingAndClosing orElse {
    case Tcp.Received(data) =>
      data.utf8String.trim match {
        case "STOP" =>
          info("Shutting down")
          sender ! Tcp.Close

        case "SIMPLE" =>
          info("Switching to simple echoing mode")
          context.become(simpleEchoing)

        case x =>
          sender ! Tcp.Write(data, ack = 'SentOk)
          context.become(waitingForAck)
      }
  }

  def waitingForAck: Receive = connectingAndClosing orElse {
    case Tcp.Received(data) =>
      sender ! Tcp.StopReading
      context.become(waitingForAckWithQueuedData(Queue(data)))

    case 'SentOk =>
      context.become(ackedEchoing)
  }

  def waitingForAckWithQueuedData(queuedData: Queue[ByteString]): Receive = connectingAndClosing orElse {
    case Tcp.Received(data) =>
      context.become(waitingForAckWithQueuedData(queuedData.enqueue(data)))

    case 'SentOk if queuedData.isEmpty =>
      sender ! Tcp.ResumeReading
      context.become(ackedEchoing)

    case 'SentOk =>
      // for brevity we don't interpret STOP and SIMPLE commands here
      sender ! Tcp.Write(queuedData.head, ack = 'SentOk)
      context.become(waitingForAckWithQueuedData(queuedData.tail))
  }

  def connectingAndClosing: Receive = {
    case Tcp.Connected(_, _) =>
      sender ! Tcp.Register(self)
      log.debug("Registered for new connection")

    case x: Tcp.ConnectionClosed =>
      log.debug("Connection closed: {}", x)
      context.system.shutdown()
  }

  def info(msg: String): Unit = {
    log.info(msg)
    sender ! Tcp.Write(ByteString(msg + '\n'))
  }
}