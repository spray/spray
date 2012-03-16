package cc.spray.examples

import cc.spray.io._
import akka.actor.{Props, ActorSystem}

object Main extends App {
  val system = ActorSystem("EchoServer")
  val ioWorker = new IoWorker(system).start()
  system.actorOf(Props(new EchoServer(ioWorker)), name = "echo-server") ! IoServer.Bind("localhost", 23456)
}

class EchoServer(ioWorker: IoWorker) extends IoServer(ioWorker) {

  override def receive = super.receive orElse {
    case IoWorker.Received(handle, buffer) =>
      new String(buffer.array).trim match {
        case "STOP" =>
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