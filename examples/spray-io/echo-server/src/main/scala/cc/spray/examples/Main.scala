package cc.spray.examples

import akka.actor.{Props, ActorSystem}
import akka.pattern.ask
import akka.util.duration._
import cc.spray.util._
import cc.spray.io._


object Main extends App {
  // we need an ActorSystem to host our application in
  val system = ActorSystem("echo-server")

  // create and start an IoWorker
  val ioWorker = new IoWorker(system).start()

  // and our actual server "service" actor
  val server = system.actorOf(
    Props(new EchoServer(ioWorker)),
    name = "echo-server"
  )

  // we bind the server to a port on localhost and hook
  // in a continuation that informs us when bound
  server
    .ask(IoServer.Bind("localhost", 23456))(1.second)
    .onSuccess {
    case IoServer.Bound(endpoint) =>
      println("\nBound echo-server to " + endpoint)
      println("Run `telnet localhost 23456`, type something and press RETURN. Type `STOP` to exit...\n")
  }

  // finally we drop the main thread but hook the shutdown of
  // our IoWorker into the shutdown of the applications ActorSystem
  system.registerOnTermination {
    ioWorker.stop()
  }
}

class EchoServer(ioWorker: IoWorker) extends IoServer(ioWorker) {

  override def receive = super.receive orElse {
    case IoWorker.Received(handle, buffer) =>
      buffer.array.asString.trim match {
        case "STOP" =>
          ioWorker ! IoWorker.Send(handle, BufferBuilder("Shutting down...").toByteBuffer)
          log.info("Shutting down")
          context.system.shutdown()
        case x =>
          log.debug("Received '{}', echoing ...", x)
          ioWorker ! IoWorker.Send(handle, buffer)
      }

    case IoWorker.AckSend(_) =>
      log.debug("Send completed")

    case IoWorker.Closed(_, reason) =>
      log.debug("Connection closed: {}", reason)
  }

}