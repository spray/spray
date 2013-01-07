package spray.examples

import scala.concurrent.duration._
import akka.actor.{Props, ActorSystem}
import akka.pattern.ask
import spray.util._
import spray.io._
import java.net.InetSocketAddress
import spray.io.IOBridge.Key


object Main extends App {
  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("echo-server")

  // and our actual server "service" actor
  val server = system.actorOf(Props(new EchoServer), name = "echo-server")

  // we bind the server to a port on localhost and hook
  // in a continuation that informs us when bound
  server
    .ask(IOServer.Bind("localhost", 23456))(1 second span)
    .onSuccess { case IOServer.Bound(endpoint, _) =>
      println("\nBound echo-server to " + endpoint)
      println("Run `telnet localhost 23456`, type something and press RETURN. Type `STOP` to exit...\n")
    }
}

class EchoServer extends IOServer {
  val ioBridge = IOExtension(context.system).ioBridge()

  override def bound(endpoint: InetSocketAddress, bindingKey: Key, bindingTag: Any): Receive =
    super.bound(endpoint, bindingKey, bindingTag) orElse {

      case IOBridge.Received(handle, buffer) =>
        buffer.array.asString.trim match {
          case "STOP" =>
            ioBridge ! IOBridge.Send(handle, BufferBuilder("Shutting down...").toByteBuffer)
            log.info("Shutting down")
            context.system.shutdown()
          case x =>
            log.debug("Received '{}', echoing ...", x)
            ioBridge ! IOBridge.Send(handle, buffer, Some('SentOk))
        }

      case 'SentOk =>
        log.debug("Send completed")

      case IOBridge.Closed(_, reason) =>
        log.debug("Connection closed: {}", reason)
    }

}