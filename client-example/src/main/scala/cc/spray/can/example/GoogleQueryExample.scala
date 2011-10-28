package cc.spray.can.example

import java.util.concurrent.TimeUnit

import akka.actor._
import akka.config.Supervision._
import akka.dispatch.Future
import cc.spray.can.HttpClient._
import cc.spray.can.{HttpResponse, HttpRequest, HttpClient}

object GoogleQueryExample extends App {

  println("Starting HttpClient actor...")
  Supervisor(
    SupervisorConfig(
      OneForOneStrategy(List(classOf[Exception]), 3, 100),
      List(Supervise(Actor.actorOf(new HttpClient()), Permanent))))


  val queries = Seq("iphone 4 case", "hdmi cable", "iphone 4 screen protector", "iphone charger", "nail art",
  "iphone 3gs case", "coupons", "hello kitty", "wii remote", "iphone 4", "htc evo case", "headphones",
  "power balance wristband", "ipod touch 4th generation case", "webcam", "hdmi cables", "wireless mouse",
  "mens watches", "p90x", "ipad case", "iphone 3gs screen protector", "iphone car charger", "iphone 4 cases",
  "kindle cover", "ipod shuffle charger", "ipod charger", "htc evo screen protector", "usb hub", "ralph lauren",
  "beads", "ipad 2 case", "vga cable", "sunglasses", "iphone 3g case", "iphone 4 car charger")

  val requests = queries.map(q => HttpRequest(uri = "/search?q=" + q.replace(" ", "+")))

  println("Running google queries in a single pipelined request...")
  val result = timed(HttpDialog("www.google.com").send(requests).end)
  result.onResult(printResult andThen secondRun)
  result.onException(printError andThen shutdown)

  def secondRun: PartialFunction[Any, Unit] = {
    case _ =>
      println("Running google queries as separate requests (in parallel) ...")
      val result = timed(Future.sequence(requests.map(r => HttpDialog("www.google.com").send(r).end), 100000))
      result.onResult(printResult andThen shutdown)
      result.onException(printError andThen shutdown)
  }

  def printResult: PartialFunction[(Seq[HttpResponse], Long), Unit] = {
    case (responses, time) =>
      println(responses.map(_.bodyAsString.length).mkString("Result bytes: ", ", ", "."))
      val rate = queries.size * 1000 / time
      println("Completed: " + queries.size + " requests in " + time + " ms at a rate of  " + rate + " req/sec\n")
  }

  def printError: PartialFunction[Throwable, Unit] = {
    case e: Exception => println("Error: " + e)
  }

  def shutdown: PartialFunction[Any, Unit] = {
    case _ =>
      Scheduler.scheduleOnce(() => Actor.registry.foreach(_ ! PoisonPill), 1000, TimeUnit.MILLISECONDS)
  }

  def timed[T](block: => Future[T]) = {
    val startTime = System.currentTimeMillis
    block.map(_ -> (System.currentTimeMillis - startTime))
  }
}