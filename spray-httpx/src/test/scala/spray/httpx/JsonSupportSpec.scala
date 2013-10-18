package spray.httpx

import org.specs2.mutable.Specification
import org.json4s.DefaultFormats
import play.api.libs.json.Json
import spray.httpx.unmarshalling._
import spray.httpx.marshalling._
import spray.json._
import spray.http._

case class Employee(fname: String, name: String, age: Int, id: Long, boardMember: Boolean) {
  require(!boardMember || age > 40, "Board members must be older than 40")
}

object Employee {
  val simple = Employee("Frank", "Smith", 42, 12345, false)
  val json = """{"fname":"Frank","name":"Smith","age":42,"id":12345,"boardMember":false}"""

  val utf8 = Employee("Fränk", "Smi√", 42, 12345, false)
  val utf8json =
    """{
      |  "fname": "Fränk",
      |  "name": "Smi√",
      |  "age": 42,
      |  "id": 12345,
      |  "boardMember": false
      |}""".stripMargin.getBytes(HttpCharsets.`UTF-8`.nioCharset)
}

class SprayJsonSupportSpec extends Specification with SprayJsonSupport {

  object MyJsonProtocol extends DefaultJsonProtocol {
    implicit val employeeFormat = jsonFormat5(Employee.apply)
  }
  import MyJsonProtocol._
  implicit def sprayJsonMarshaller[T](implicit w: RootJsonWriter[T]) = super.sprayJsonMarshaller(w, CompactPrinter)

  "The SprayJsonSupport" should {
    "provide unmarshalling support for a case class" in {
      HttpEntity(ContentTypes.`application/json`, Employee.json).as[Employee] === Right(Employee.simple)
    }
    "provide marshalling support for a case class" in {
      marshal(Employee.simple) === Right(HttpEntity(ContentTypes.`application/json`, Employee.json))
    }
    "use UTF-8 as the default charset for JSON source decoding" in {
      HttpEntity(MediaTypes.`application/json`, Employee.utf8json).as[Employee] === Right(Employee.utf8)
    }
  }
}

class Json4sSupportSpec extends Specification with Json4sSupport {
  val json4sFormats = DefaultFormats

  "The Json4sSupport" should {
    "provide unmarshalling support for a case class" in {
      HttpEntity(ContentTypes.`application/json`, Employee.json).as[Employee] === Right(Employee.simple)
    }
    "provide marshalling support for a case class" in {
      marshal(Employee.simple) === Right(HttpEntity(ContentTypes.`application/json`, Employee.json))
    }
    "use UTF-8 as the default charset for JSON source decoding" in {
      HttpEntity(MediaTypes.`application/json`, Employee.utf8json).as[Employee] === Right(Employee.utf8)
    }
  }
}

class Json4sJacksonSupportSpec extends Specification with Json4sJacksonSupport {
  val json4sJacksonFormats = DefaultFormats

  "The Json4sJacksonSupport" should {
    "provide unmarshalling support for a case class" in {
      HttpEntity(ContentTypes.`application/json`, Employee.json).as[Employee] === Right(Employee.simple)
    }
    "provide marshalling support for a case class" in {
      marshal(Employee.simple) === Right(HttpEntity(ContentTypes.`application/json`, Employee.json))
    }
    "use UTF-8 as the default charset for JSON source decoding" in {
      HttpEntity(MediaTypes.`application/json`, Employee.utf8json).as[Employee] === Right(Employee.utf8)
    }
  }
}

class LiftJsonSupportSpec extends Specification with LiftJsonSupport {
  val liftJsonFormats = net.liftweb.json.DefaultFormats

  "The LiftJsonSupport" should {
    "provide unmarshalling support for a case class" in {
      HttpEntity(ContentTypes.`application/json`, Employee.json).as[Employee] === Right(Employee.simple)
    }
    "provide marshalling support for a case class" in {
      marshal(Employee.simple) === Right(HttpEntity(ContentTypes.`application/json`, Employee.json))
    }
    "use UTF-8 as the default charset for JSON source decoding" in {
      HttpEntity(MediaTypes.`application/json`, Employee.utf8json).as[Employee] === Right(Employee.utf8)
    }
  }
}

class PlayJsonSupportSpec extends Specification with PlayJsonSupport {
  implicit val employeeReader = Json.reads[Employee]
  implicit val employeeWriter = Json.writes[Employee]

  "The PlayJsonSupport" should {
    "provide unmarshalling capability for case classes with an in-scope Reads[T]" in {
      HttpEntity(ContentTypes.`application/json`, Employee.json).as[Employee] === Right(Employee.simple)
    }
    "provide marshalling capability for case classes with an in-scope Writes[T]" in {
      marshal(Employee.simple) === Right(HttpEntity(ContentTypes.`application/json`, Employee.json))
    }
    "use UTF-8 as the default charset for JSON source decoding" in {
      HttpEntity(MediaTypes.`application/json`, Employee.utf8json).as[Employee] === Right(Employee.utf8)
    }
  }
}