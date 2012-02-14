package cc.spray.examples

import cc.spray.io.config.IoServerConfig
import akka.actor.{ActorLogging, ActorSystem, Props, ActorRef}
import cc.spray.io._

object Main extends App {
  val system = ActorSystem("EchoServer")
  val ioWorker = system.actorOf(Props(new IoWorker()), name = "io-worker")
  system.actorOf(Props(new EchoServer(ioWorker)), name = "echo-server")
}

class EchoServer(ioWorker: ActorRef)
  extends IoServerActor(IoServerConfig("localhost", 23456), ioWorker)
  with ActorLogging {

  override protected def receive = super.receive orElse {

    case Received(handle, buffer) =>
      new String(buffer.array).trim match {
        case "STOP" =>
          log.info("Shutting down")
          context.system.shutdown()
        case x =>
          log.debug("Received '{}'", x)
          ioWorker ! Send(handle, buffer)
      }

    case SendCompleted(_) =>
      log.info("Send completed")

    case Closed(_, reason) =>
      log.info("Connection closed: {}", reason)
  }

}