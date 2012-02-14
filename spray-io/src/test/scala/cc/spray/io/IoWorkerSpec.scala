package cc.spray.io

import config.IoServerConfig
import org.specs2.Specification
import akka.actor.{ActorRef, Props, ActorSystem}
import org.specs2.specification.Step
import akka.pattern.ask
import java.nio.ByteBuffer
import akka.util.{Timeout, Duration}
import java.util.concurrent.CountDownLatch
import akka.dispatch.Await

class IoWorkerSpec extends Specification {
  implicit val system = ActorSystem("IoWorkerspec")
  val port = 23456
  val serverUp = new CountDownLatch(1)

  class TestServer(ioWorker: ActorRef) extends IoServerActor(IoServerConfig("localhost", port), ioWorker) {
    override protected def receive = ({
        case Received(handle, buffer) => ioWorker ! Send(handle, buffer)
        case x: Bound =>
          super.receive(x)
          serverUp.countDown()
    }: Receive) orElse super.receive
  }

  class TestClient(ioWorker: ActorRef) extends IoClientActor(ioWorker) {
    var requests = Map.empty[Handle, ActorRef]
    override protected def receive = super.receive orElse {
      case (x: String, handle: Handle) =>
        requests += handle -> sender
        ioWorker ! Send(handle, ByteBuffer.wrap(x.getBytes))
      case Received(handle, buffer) =>
        requests(handle) ! new String(buffer.array, 0, buffer.limit)
    }
  }

  lazy val client = {
    val ioWorker = system.actorOf(Props(new IoWorker()), name = "io-worker")
    system.actorOf(Props(new TestServer(ioWorker)), name = "test-server")
    serverUp.await()
    system.actorOf(Props(new TestClient(ioWorker)), name = "test-client")
  }

  def is = sequential^
  "This spec exercises an IoWorker instance against itself" ^
                                                            p^
  "simple one-request dialog"                               ! oneRequestDialog^
                                                            Step(system.shutdown())

  implicit val timeout: Timeout = Duration("500 ms")

  def oneRequestDialog = {
    val resp = for {
      handle <- (client ? ClientConnect("localhost", port)).mapTo[Handle]
      response <- (client ? ("Echoooo" -> handle)).mapTo[String]
    } yield response
    Await.result(resp, timeout.duration) === "Echoooo"
  }

}
