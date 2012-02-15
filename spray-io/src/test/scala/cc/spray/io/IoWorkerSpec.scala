package cc.spray.io

import org.specs2.Specification
import org.specs2.specification.Step
import akka.pattern.ask
import java.nio.ByteBuffer
import akka.util.{Timeout, Duration}
import akka.actor._
import akka.dispatch.{Future, Await}
import org.specs2.matcher.Matcher

class IoWorkerSpec extends Specification {
  implicit val timeout: Timeout = Duration("500 ms")
  implicit val system = ActorSystem("IoWorkerSpec")
  val port = 23456

  class TestServer(ioWorker: IoWorker) extends IoServer(ioWorker) {
    override def receive = super.receive orElse {
      case IoWorker.Received(handle, buffer) => ioWorker ! IoWorker.Send(handle, buffer)
    }
  }

  class TestClient(ioWorker: IoWorker) extends IoClient(ioWorker) {
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
    "hammer time"                                             ! hammerTime^
                                                              Step(stop())

  def start() {
    val bound = Await.result(server ? IoServer.Bind("localhost", port), timeout.duration)
    assert(bound.isInstanceOf[IoServer.Bound])
  }

  def oneRequestDialog = {
    Await.result(request("Echoooo"), timeout.duration) === "Echoooo"
  }

  def hammerTime = {
    val requests = Future.traverse((1 to 100).toList) { i => request("Ping" + i).map(i -> _) }
    val beOk: Matcher[(Int, String)] = ({ t:(Int, String) => t._2 == "Ping" + t._1 }, "not ok")
    Await.result(requests, timeout.duration) must beOk.forall
  }

  def request(payload: String) = {
    for {
      IoClient.Connected(handle) <- (client ? IoClient.Connect("localhost", port)).mapTo[IoClient.Connected]
      response <- (client ? (payload -> handle)).mapTo[String]
    } yield {
      worker ! IoWorker.Close(handle, ProtocolClose)
      response
    }
  }

  def stop() {
    system.shutdown()
    worker.stop().get.await()
  }

}
