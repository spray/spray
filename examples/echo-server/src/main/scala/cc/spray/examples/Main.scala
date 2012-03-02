package cc.spray.examples

import cc.spray.io._
import akka.actor.{Props, ActorSystem}

object Main extends App {
  val system = ActorSystem("EchoServer")
  system.actorOf(Props(new EchoServer), name = "echo-server") ! IoServer.Bind("localhost", 23456)
}

class EchoServer extends IoServer(new IoWorker().start()) {

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
      log.info("Send completed")

    case IoWorker.Closed(_, reason) =>
      log.info("Connection closed: {}", reason)
  }

}