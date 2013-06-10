package docs

import org.specs2.mutable.Specification
import scala.concurrent.Future
import spray.http._

class ResponseTransformationExamplesSpec extends Specification {

  "example" in {
    //# part-1
    import scala.concurrent.ExecutionContext.Implicits.global // for futures

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

    pending
  }

}
