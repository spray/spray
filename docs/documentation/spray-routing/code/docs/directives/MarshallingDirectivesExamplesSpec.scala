package docs.directives

import spray.http._
import spray.httpx.SprayJsonSupport
import spray.httpx.unmarshalling.Unmarshaller
import spray.json.DefaultJsonProtocol
import HttpHeaders._
import MediaTypes._

//# person-case-class
case class Person(name: String, favoriteNumber: Int)

//# person-json-support
object PersonJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
   implicit val PortofolioFormats = jsonFormat2(Person)
}
//#

class MarshallingDirectivesExamplesSpec extends DirectivesSpec {

  "example-entity-with-json" in {
    import PersonJsonSupport._

    val route = post {
      entity(as[Person]) { person =>
        complete(s"Person: ${person.name} - favorite number: ${person.favoriteNumber}")
      }
    }

    Post("/", HttpEntity(`application/json`, """{ "name": "Jane", "favoriteNumber" : 42 }""" )) ~>
      route ~> check {
        entityAs[String] === "Person: Jane - favorite number: 42"
      }
  }

  "example-produce-with-json" in {
    import PersonJsonSupport._

    val findPerson = (f: Person => Unit) => {

      //... some processing logic...

      //complete the request
      f(Person("Jane", 42))
    }

    val route = get {
      produce(instanceOf[Person]) { completionFunction => ctx => findPerson(completionFunction) }
    }

    Get("/") ~> route ~> check {
      mediaType === `application/json`
      entityAs[String] must contain(""""name": "Jane"""")
      entityAs[String] must contain(""""favoriteNumber": 42""")
    }
  }

  "example-handleWith-with-json" in {
    import PersonJsonSupport._

    val updatePerson = (person: Person) => {

      //... some processing logic...

      //return the person
      person
    }

    val route = post {
      handleWith(updatePerson)
    }

     Post("/", HttpEntity(`application/json`, """{ "name": "Jane", "favoriteNumber" : 42 }""" )) ~>
      route ~> check {
        mediaType === `application/json`
        entityAs[String] must contain(""""name": "Jane"""")
        entityAs[String] must contain(""""favoriteNumber": 42""")
      }
  }
}
