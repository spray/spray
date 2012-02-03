package cc.spray.examples.stopwatch

import cc.spray._
import directives.IntNumber
import http._
import StatusCodes._
import MediaTypes._
import HttpHeaders._

trait StopWatchService extends Directives with StopWatchMarshallers {
  
  sealed trait StopWatch {
    def time: Long
    def start: StopWatch
    def stop: StopWatch
    def state: String
  }
  
  case class Running(startTime: Long) extends StopWatch {
    def time = currentTime - startTime
    def start = this
    def stop = Stopped(time)
    def state = "running"
  }
  
  case class Stopped(time: Long) extends StopWatch {
    def start = Running(currentTime - time)
    def stop = this
    def state = "stopped"
  }
  
  def currentTime = System.currentTimeMillis
  
  val watches = collection.mutable.Map.empty[Int, StopWatch]    
  
  val stopWatchService = {
    path("") {
      getFromResource("main.html")
    } ~
    transformResponse(_.withContentTransformed(wrapWithBackLink)) {
      path("watches") {
        post {
          createNewWatch()
        } ~
        get {
          completeWith(watches)
        }      
      } ~
      pathPrefix("watch" / IntNumber) { ix =>
        path("") {
          delete { ctx =>
            watches.remove(ix)
            ctx.complete("Removed watch " + ix)
          } 
          get {
            completeWith(watches(ix))
          }
        } ~
        path("start") {
          post { ctx =>
            watches(ix) = watches(ix).start
            ctx.complete("Started watch " + ix)
          }
        } ~
        path("stop") {
          post { ctx =>
            watches(ix) = watches(ix).stop
            ctx.complete("Stopped watch " + ix)
          }
        } ~
        path("clear") {
          post { ctx =>
            watches(ix) = Stopped(0)
            ctx.complete("Cleared watch " + ix)
          }
        }
      }
    }
  }
  
  // enable method tunneling via query parameter "method"
  override def method(m: HttpMethod) = super.method(m) | parameter('method ! m.toString.toLowerCase)
  
  def createNewWatch(): Route = { ctx =>
    val newWatchUri = "/watch/" + watches.size
    watches(watches.size) = Stopped(0) // all new watches start stopped
    ctx.complete(HttpResponse(Created, Location(newWatchUri) :: Nil, HttpContent("New stopwatch created at " + newWatchUri)))
  }
  
  def wrapWithBackLink(content: HttpContent) = content.contentType match {
    case ContentType(`text/plain`, charset) => {
      val html =
        <html>
          <body>
            <p>{content.as[String].right.get}</p>
            <a href="/watches">Back</a>
          </body>
        </html>
      NodeSeqMarshaller.marshal(html, ContentType(`text/html`, charset))
    }
    case _ => content
  }
  
}