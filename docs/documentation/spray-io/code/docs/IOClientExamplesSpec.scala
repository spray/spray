package docs

import org.specs2.mutable.Specification


class IOClientExamplesSpec extends Specification {

  //# example-1
  import akka.util.{Duration, Timeout}
  import akka.pattern.ask
  import akka.actor._
  import spray.util._
  import spray.io._

  class EchoClient(ioBridge: IOBridge) extends IOClient(ioBridge) {
    var pingSender: Option[ActorRef] = None

    override def receive = myReceive orElse super.receive

    def myReceive: Receive = {
      case EchoClient.Ping(handle) =>
        pingSender = Some(sender)
        ioBridge ! IOBridge.Send(handle, BufferBuilder("PING").toByteBuffer)

      case IOClient.Received(handle, buffer) =>
        pingSender.foreach(_ ! EchoClient.PingResponse(buffer.drainToString))

      case IOClient.Closed(_, reason) =>
        log.debug("Connection closed: {}", reason)
    }
  }

  object EchoClient {
    case class Ping(handle: Handle) extends Command
    case class PingResponse(response: String) extends Event
  }

  class EchoServer(ioBridge: IOBridge) extends IOServer(ioBridge) {
    override def receive = super.receive orElse {
      case IOServer.Received(handle, buffer) if buffer.duplicate.drainToString == "PING" =>
        ioBridge ! IOBridge.Send(handle, BufferBuilder("PONG").toByteBuffer)
        ioBridge ! IOBridge.Close(handle, CleanClose)
    }
  }

  val system = ActorSystem()

  val ioBridge = new IOBridge(system).start()
  //#

  "example-1" in {

    val server = system.actorOf(Props(new EchoServer(ioBridge)), "server")
    val client = system.actorOf(Props(new EchoClient(ioBridge)), "client")

    implicit val timeout: Timeout = Duration("1 sec") // timeout for the asks below

    // bind the server to a local port and wait for it to come up
    server.ask(IOServer.Bind("localhost", 46468)).await

    // ask the client to connect to the server and wait for the connection to come up
    val IOClient.Connected(handle) = client.ask(IOClient.Connect("localhost", 46468)).await

    // ping the server and wait for the response
    val EchoClient.PingResponse(response) = client.ask(EchoClient.Ping(handle)).await

    response === "PONG"

  }

  step {
    system.shutdown()  // example-1
    ioBridge.stop()    // example-1
  }

}
