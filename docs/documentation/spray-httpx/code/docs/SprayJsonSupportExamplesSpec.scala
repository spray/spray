package docs

import org.specs2.mutable.Specification


class SprayJsonSupportExamplesSpec extends Specification {

  //# example-1
  import spray.json.DefaultJsonProtocol
  import spray.httpx.unmarshalling._
  import spray.httpx.marshalling._
  import spray.http._
  import HttpCharsets._
  import MediaTypes._

  case class Person(name: String, firstName: String, age: Int)

  object MyJsonProtocol extends DefaultJsonProtocol {
    implicit val PersonFormat = jsonFormat3(Person)
  }

  //#

  "example-1" in {
    import MyJsonProtocol._
    import spray.httpx.SprayJsonSupport._
    import spray.util._

    val bob = Person("Bob", "Parr", 32)
    val body = HttpEntity(
      contentType = ContentType(`application/json`, `UTF-8`),
      string =
        """|{
           |  "name": "Bob",
           |  "firstName": "Parr",
           |  "age": 32
           |}""".stripMarginWithNewline("\n")
    )

    marshal(bob) === Right(body)
    body.as[Person] === Right(bob)
  }
}
