package spray.examples

import akka.actor.{Props, ActorSystem}
import akka.pattern.ask
import akka.util.duration._
import spray.util._
import spray.io._


object Main extends App {
  // we need an ActorSystem to host our application in
  val system = ActorSystem("echo-server")

  // create and start an IOBridge
  val ioBridge = new IOBridge(system).start()

  // and our actual server "service" actor
  val server = system.actorOf(
    Props(new EchoServer(ioBridge)),
    name = "echo-server"
  )

  // we bind the server to a port on localhost and hook
  // in a continuation that informs us when bound
  server
    .ask(IOServer.Bind("localhost", 23456))(1.second)
    .onSuccess {
    case IOServer.Bound(endpoint, _) =>
      println("\nBound echo-server to " + endpoint)
      println("Run `telnet localhost 23456`, type something and press RETURN. Type `STOP` to exit...\n")
  }

  // finally we drop the main thread but hook the shutdown of
  // our IOBridge into the shutdown of the applications ActorSystem
  system.registerOnTermination {
    ioBridge.stop()
  }
}

class EchoServer(ioBridge: IOBridge) extends IOServer(ioBridge) {

  override def receive = super.receive orElse {
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