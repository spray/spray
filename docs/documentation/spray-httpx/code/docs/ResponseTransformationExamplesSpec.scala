package docs

import org.specs2.mutable.Specification
import akka.dispatch.Future
import akka.actor.ActorSystem
import spray.testkit.Specs2Utils.compileOnly
import spray.http._

class ResponseTransformationExamplesSpec extends Specification {

  "example" in compileOnly {
    //# part-1
    implicit val system = ActorSystem() // for futures

    val sendReceive: HttpRequest => Future[HttpResponse] = // ...
      null // hide
    //#

    //# part-2
    val removeCookieHeaders: HttpResponse => HttpResponse =
      r => r.withHeaders(r.headers.filter(_.isNot("set-cookie")))
    //#

    //# part-3
    import spray.httpx.ResponseTransformation._

    val pipeline: HttpRequest => Future[HttpResponse] =
      sendReceive ~> removeCookieHeaders
    //#
  }
}
