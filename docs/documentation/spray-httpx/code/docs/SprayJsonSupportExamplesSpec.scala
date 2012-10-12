package docs

import org.specs2.mutable.Specification


class SprayJsonSupportExamplesSpec extends Specification {

  //# example-1
  import spray.httpx.unmarshalling.pimpHttpEntity
  import spray.json.DefaultJsonProtocol
  import spray.httpx.marshalling._
  import spray.http._
  import MediaTypes.`application/json`

  case class Person(name: String, firstName: String, age: Int)

  object MyJsonProtocol extends DefaultJsonProtocol {
    implicit val PersonFormat = jsonFormat3(Person)
  }

  //#

  "example-1" in {
    import MyJsonProtocol._
    import spray.httpx.SprayJsonSupport._

    val bob = Person("Bob", "Parr", 32)
    val body = HttpBody(`application/json`,
      """|{
         |  "name": "Bob",
         |  "firstName": "Parr",
         |  "age": 32
         |}""".stripMargin
    )

    marshal(bob) === Right(body)
    body.as[Person] === Right(bob)
  }
}
