package docs

import org.specs2.mutable.Specification


class MarshallingExamplesSpec extends Specification {

  //# example-1
  import spray.http._
  import spray.httpx.marshalling._

  val `application/vnd.acme.person` =
    MediaTypes.register(MediaType.custom("application/vnd.acme.person"))

  case class Person(name: String, firstName: String, age: Int)

  object Person {
    implicit val PersonMarshaller =
      Marshaller.of[Person](`application/vnd.acme.person`) { (value, contentType, ctx) =>
        val Person(name, first, age) = value
        val string = "Person: %s, %s, %s".format(name, first, age)
        ctx.marshalTo(HttpEntity(contentType, string))
      }
  }
  //#

  "example-1" in {

    marshal(Person("Bob", "Parr", 32)) ===
      Right(HttpEntity(`application/vnd.acme.person`, "Person: Bob, Parr, 32"))
  }

}
