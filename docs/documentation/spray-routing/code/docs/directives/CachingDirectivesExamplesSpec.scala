package docs.directives

import spray.routing.directives.CachingDirectives
import spray.http.HttpHeaders.`Cache-Control`
import spray.http.CacheDirectives

class CachingDirectivesExamplesSpec extends DirectivesSpec with CachingDirectives {
  "alwaysCache" in {
    var i = 0
    val route =
      cache(routeCache()) {
        complete {
          i += 1
          i.toString
        }
      }

    Get("/") ~> route ~> check {
      responseAs[String] === "1"
    }
    // now cached
    Get("/") ~> route ~> check {
      responseAs[String] === "1"
    }
    // caching prevented
    Get("/") ~> `Cache-Control`(CacheDirectives.`no-cache`) ~> route ~> check {
      responseAs[String] === "2"
    }
  }
  "cache-0" in {
    var i = 0
    val route =
      cache(routeCache()) {
        complete {
          i += 1
          i.toString
        }
      }

    Get("/") ~> route ~> check {
      responseAs[String] === "1"
    }
    // now cached
    Get("/") ~> route ~> check {
      responseAs[String] === "1"
    }
    Get("/") ~> route ~> check {
      responseAs[String] === "1"
    }
  }
  "cachingProhibited" in {
    val route =
      cachingProhibited {
        complete("abc")
      }

    Get("/") ~> route ~> check {
      handled === false
    }
    Get("/") ~> `Cache-Control`(CacheDirectives.`no-cache`) ~> route ~> check {
      responseAs[String] === "abc"
    }
  }
}
