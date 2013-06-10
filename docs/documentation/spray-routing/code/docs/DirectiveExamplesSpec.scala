package docs

import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest

//# example-1
import spray.routing._
import Directives._

//#

class DirectiveExamplesSpec extends Specification with Specs2RouteTest {

  "example-1, example-2" in {
    val route: Route =
      path("order" / IntNumber) { id =>
        get {
          complete {
            "Received GET request for order " + id
          }
        } ~
        put {
          complete {
            "Received PUT request for order " + id
          }
        }
      }
    verify(route) // hide
  }

  "example-3" in {
    def innerRoute(id: Int): Route =
      get {
        complete {
          "Received GET request for order " + id
        }
      } ~
      put {
        complete {
          "Received PUT request for order " + id
        }
      }

    val route: Route = path("order" / IntNumber) { id => innerRoute(id) }
    verify(route) // hide
  }

  "example-4" in {
    val route =
      path("order" / IntNumber) { id =>
        (get | put) { ctx =>
          ctx.complete("Received " + ctx.request.method + " request for order " + id)
        }
      }
    verify(route) // hide
  }

  "example-5" in {
    val getOrPut = get | put
    val route =
      path("order" / IntNumber) { id =>
        getOrPut { ctx =>
          ctx.complete("Received " + ctx.request.method + " request for order " + id)
        }
      }
    verify(route) // hide
  }

  "example-6" in {
    val getOrPut = get | put
    val route =
      (path("order" / IntNumber) & getOrPut) { id => ctx =>
        ctx.complete("Received " + ctx.request.method + " request for order " + id)
      }
    verify(route) // hide
  }

  "example-7" in {
    val orderGetOrPut = path("order" / IntNumber) & (get | put)
    val route =
      orderGetOrPut { id => ctx =>
        ctx.complete("Received " + ctx.request.method + " request for order " + id)
      }
    verify(route) // hide
  }

  "example-8" in {
    val orderGetOrPut = path("order" / IntNumber) & (get | put)
    val method = extract(_.request.method)
    val route =
      orderGetOrPut { id =>
        method { m =>
          complete("Received " + m + " request for order " + id)
        }
      }
    verify(route) // hide
  }

  "example-9" in {
    val orderGetOrPut = path("order" / IntNumber) & (get | put)
    val method = extract(_.request.method)
    val route =
      (orderGetOrPut & method) { (id, m) =>
        complete("Received " + m + " request for order " + id)
      }
    verify(route) // hide
  }

  "example-A" in {
    val orderGetOrPutMethod =
      path("order" / IntNumber) & (get | put) & extract(_.request.method)
    val route =
      orderGetOrPutMethod { (id, m) =>
        complete("Received " + m + " request for order " + id)
      }
    verify(route) // hide
  }

  def verify(route: Route) = {
    Get("/order/42") ~> route ~> check { entityAs[String] === "Received GET request for order 42" }
    Put("/order/42") ~> route ~> check { entityAs[String] === "Received PUT request for order 42" }
    Get("/") ~> route ~> check { handled must beFalse }
  }
}
