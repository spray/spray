package docs

import org.specs2.mutable.Specification


class MarshallingExamplesSpec extends Specification {

  //# example-1
  import spray.http.HttpBody
  import spray.httpx.marshalling._
  import spray.http.MediaTypes._

  val `application/vnd.acme.person` =
    register(CustomMediaType("application/vnd.acme.person"))

  case class Person(name: String, firstName: String, age: Int)

  object Person {
    implicit val PersonMarshaller =
      Marshaller.of[Person](`application/vnd.acme.person`) { (value, contentType, ctx) =>
        val Person(name, first, age) = value
        val string = "Person: %s, %s, %s".format(name, first, age)
        ctx.marshalTo(HttpBody(contentType, string))
      }
  }
  //#

  "example-1" in {

    marshal(Person("Bob", "Parr", 32)) ===
      Right(HttpBody(`application/vnd.acme.person`, "Person: Bob, Parr, 32"))
  }

}
