package cc.spray.examples

import cc.spray.io._
import akka.actor.{Props, ActorSystem}
import akka.pattern.ask
import akka.util.duration._

object Main extends App {
  val system = ActorSystem("EchoServer")
  val ioWorker = new IoWorker(system).start()
  val server = system.actorOf(Props(new EchoServer(ioWorker)), name = "echo-server")
  server.ask(IoServer.Bind("localhost", 23456))(1.second) onSuccess {
    case IoServer.Bound(endpoint) =>
      println("\nBound echo-server to " + endpoint)
      println("Run `telnet localhost 23456` and type something. Type `STOP` to exit...\n")
  }
}

class EchoServer(ioWorker: IoWorker) extends IoServer(ioWorker) {

  override def receive = super.receive orElse {
    case IoWorker.Received(handle, buffer) =>
      new String(buffer.array).trim match {
        case "STOP" =>
          ioWorker ! IoWorker.Send(handle, BufferBuilder("Shutting down...").toByteBuffer)
          log.info("Shutting down")
          ioWorker.stop()
          context.system.shutdown()
        case x =>
          log.debug("Received '{}', echoing ...", x)
          ioWorker ! IoWorker.Send(handle, buffer)
      }

    case IoWorker.SendCompleted(_) =>
      log.debug("Send completed")

    case IoWorker.Closed(_, reason) =>
      log.debug("Connection closed: {}", reason)
  }

}