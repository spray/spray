package spray.examples

import scala.concurrent.Future
import akka.actor.{ActorSystem, Props}
import spray.can.client.{HttpDialog, HttpClient}
import spray.http.{HttpResponse, HttpRequest}
import spray.io.IOExtension
import spray.util._


object GoogleQueryExample extends App {
  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("google-query-example")
  import system.log

  // every spray-can HttpClient (and HttpServer) needs an IOBridge for low-level network IO
  // (but several servers and/or clients can share one)
  val ioBridge = IOExtension(system).ioBridge()

  // create and start the spray-can HttpClient
  val httpClient = system.actorOf(
    props = Props(new HttpClient(ioBridge)),
    name = "http-client"
  )

  val queries = Seq("iphone 4 case", "hdmi cable", "iphone 4 screen protector", "iphone charger", "nail art",
    "iphone 3gs case", "coupons", "hello kitty", "wii remote", "iphone 4", "htc evo case", "headphones",
    "power balance wristband", "ipod touch 4th generation case", "webcam", "hdmi cables", "wireless mouse",
    "mens watches", "p90x", "ipad case", "iphone 3gs screen protector", "iphone car charger", "iphone 4 cases",
    "kindle cover", "ipod shuffle charger", "ipod charger", "htc evo screen protector", "usb hub", "ralph lauren",
    "beads", "ipad 2 case", "vga cable", "sunglasses", "iphone 3g case", "iphone 4 car charger")

  val requests = queries.map(q => HttpRequest(uri = "/search?q=" + q.replace(" ", "+")))

  log.info("Running {} google queries over a single connection using pipelined requests...", requests.length)
  val responseFuture = timed(HttpDialog(httpClient, "www.google.com").send(requests).end)
  responseFuture.onSuccess(printResult andThen secondRun)
  responseFuture.onFailure(printError andThen shutdown)


  def secondRun: PartialFunction[Any, Unit] = {
    case _ =>
      log.info("Running google queries as separate requests (in parallel) ...")
      def httpDialog(r: HttpRequest) = HttpDialog(httpClient, "www.google.com").send(r).end
      val responseFuture = timed(Future.sequence(requests.map(httpDialog)))
      responseFuture.onSuccess(printResult andThen shutdown)
      responseFuture.onFailure(printError andThen shutdown)
  }

  def printResult: PartialFunction[(Seq[HttpResponse], Long), Unit] = {
    case (responses, time) =>
      log.info(responses.map(_.entity.buffer.length).mkString("Result bytes: ", ", ", "."))
      val rate = queries.size * 1000 / time
      log.info("Completed: {} requests in {} ms at a rate of  {} req/sec\n", queries.size, time, rate)
  }

  def printError: PartialFunction[Throwable, Unit] = {
    case e: Exception => log.error("Error: {}", e)
  }

  def shutdown: PartialFunction[Any, Unit] = {
    case _ =>
      system.shutdown()
  }

  def timed[T](block: => Future[T]) = {
    val startTime = System.currentTimeMillis
    block.map(_ -> (System.currentTimeMillis - startTime))
  }
}
