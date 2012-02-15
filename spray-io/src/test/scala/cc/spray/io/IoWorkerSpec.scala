package cc.spray.io

import org.specs2.Specification
import org.specs2.specification.Step
import akka.pattern.ask
import java.nio.ByteBuffer
import akka.util.{Timeout, Duration}
import akka.dispatch.Await
import akka.actor._

class IoWorkerSpec extends Specification {
  implicit val timeout: Timeout = Duration("500 ms")
  val system = ActorSystem("IoWorkerSpec")
  val port = 23456

  class TestServer(ioWorker: IoWorker) extends IoServerActor(ioWorker) {
    override def receive = super.receive orElse {
      case IoWorker.Received(handle, buffer) =>
        log.info("MARK")
        ioWorker ! IoWorker.Send(handle, buffer)
    }
  }

  class TestClient(ioWorker: IoWorker) extends IoClientActor(ioWorker) {
    var requests = Map.empty[Handle, ActorRef]
    override def receive = super.receive orElse {
      case (x: String, handle: Handle) =>
        requests += handle -> sender
        ioWorker ! IoWorker.Send(handle, ByteBuffer.wrap(x.getBytes))
      case IoWorker.Received(handle, buffer) =>
        requests(handle) ! new String(buffer.array, 0, buffer.limit)
    }
  }

  lazy val worker = new IoWorker().start()
  lazy val server = system.actorOf(Props(new TestServer(worker)), name = "test-server")
  lazy val client = system.actorOf(Props(new TestClient(worker)), name = "test-client")

  def is = sequential^
    "This spec exercises an IoWorker instance against itself" ^
                                                              Step(start())^
    "simple one-request dialog"                               ! oneRequestDialog^
                                                              Step(stop())

  def start() {
    val bound = Await.result(server ? IoServerActor.Bind("localhost", port), timeout.duration)
    assert(bound.isInstanceOf[IoServerActor.Bound])
  }

  def oneRequestDialog = {
    val resp = for {
      connected <- (client ? IoClientActor.Connect("localhost", port)).mapTo[IoClientActor.Connected]
      response <- (client ? ("Echoooo" -> connected.handle)).mapTo[String]
    } yield response
    Await.result(resp, timeout.duration) === "Echoooo"
  }

  def stop() {
    system.shutdown()
    worker.stop().get.await()
  }

}
