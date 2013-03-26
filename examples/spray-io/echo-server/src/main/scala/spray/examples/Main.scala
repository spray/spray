package spray.examples

import java.net.InetSocketAddress
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

  def receive = {
    case Tcp.Connected(_, _) =>
      sender ! Tcp.Register(self)
      log.debug("Registered for new connection")

    case Tcp.Received(data) =>
      data.utf8String.trim match {
        case "STOP" =>
          sender ! Tcp.Write(ByteString("Shutting down..."))
          sender ! Tcp.Close
          log.info("Shutting down")

        case x =>
          log.debug("Received '{}', echoing ...", x)
          sender ! Tcp.Write(data, ack = 'SentOk)
      }

    case 'SentOk =>
      log.debug("Send completed")

    case x: Tcp.ConnectionClosed =>
      log.debug("Connection closed: {}", x)
      context.system.shutdown()
  }
}