package docs

import org.specs2.mutable.Specification
import spray.httpx.marshalling.Marshaller


class RequestBuildingExamplesSpec extends Specification {

  "example-1" in {
    import spray.http._
    import HttpMethods._
    import HttpHeaders._
    import MediaTypes._
    import spray.httpx.RequestBuilding._

    // simple GET requests
    Get() === HttpRequest(method = GET)
    Get("/abc") === HttpRequest(method = GET, uri = "/abc")

    // as second argument you can specify an object that is
    // to be marshalled using the in-scope marshaller for the type
    Put("/abc", "foobar") === HttpRequest(method = PUT, uri = "/abc", entity = "foobar")

    implicit val intMarshaller = Marshaller.of[Int](`application/json`) {
      (value, _, ctx) => ctx.marshalTo("{ value: %s }" format value)
    }
    Post("/int", 42) === HttpRequest(method = POST, uri = "/int", entity = "{ value: 42 }")

    // add one or more headers by chaining in the `addHeader` modifier
    Patch("/abc", "content") ~> addHeader("X-Yeah", "Naah") === HttpRequest(
      method = PATCH,
      uri = "/abc",
      entity = "content",
      headers = List(RawHeader("X-Yeah", "Naah"))
    )
  }

}
