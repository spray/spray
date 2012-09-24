package docs

import org.specs2.mutable.Specification
import cc.spray.httpx.unmarshalling.ContentExpected
import cc.spray.http.EmptyEntity
import xml.NodeSeq


class UnmarshallingExamplesSpec extends Specification {

  //# example-1
  import cc.spray.http.HttpBody
  import cc.spray.util.pimpByteArray
  import cc.spray.httpx.unmarshalling.{Unmarshaller, pimpHttpEntity}
  import cc.spray.http.MediaTypes._

  val `application/vnd.acme.person` =
    register(CustomMediaType("application/vnd.acme.person"))

  case class Person(name: String, firstName: String, age: Int)

  object Person {
    implicit val PersonUnmarshaller =
      Unmarshaller[Person](`application/vnd.acme.person`) {
        case HttpBody(contentType, buffer) =>
          // unmarshal from the string format used in the marshaller example
          val Array(_, name, first, age) =
            buffer.asString.split(":,".toCharArray).map(_.trim)
          Person(name, first, age.toInt)

        // if we had meaningful semantics for the EmptyEntity
        // we could add a case for the EmptyEntity:
        // case EmptyEntity => ...
      }
  }
  //#

  "example-1" in {

    val body = HttpBody(`application/vnd.acme.person`, "Person: Bob, Parr, 32")
    body.as[Person] === Right(Person("Bob", "Parr", 32))
  }

  "example-2 test" in {
    //# example-2
    implicit val SimplerPersonUnmarshaller =
      Unmarshaller.delegate[String, Person](`application/vnd.acme.person`) { string =>
        val Array(_, name, first, age) = string.split(":,".toCharArray).map(_.trim)
        Person(name, first, age.toInt)
      }
    //#

    val body = HttpBody(`application/vnd.acme.person`, "Person: Bob, Parr, 32")
    body.as[Person] === Right(Person("Bob", "Parr", 32))
  }

  "example-3" in {
    import cc.spray.http.MediaTypes.`text/xml`

    implicit val myNodeSeqUnmarshaller = Unmarshaller.forNonEmpty[NodeSeq]

    HttpBody(`text/xml`, "<xml>yeah</xml>").as[NodeSeq] === Right(<xml>yeah</xml>)
    EmptyEntity.as[NodeSeq] === Left(ContentExpected)
  }
}
