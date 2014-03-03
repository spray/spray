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

  val illegalEmployeeJson = """{"fname":"Little Boy","name":"Smith","age":7,"id":12345,"boardMember":true}"""
}

trait JsonSupportSpec extends Specification {
  require(getClass.getSimpleName.endsWith("Spec"))
  // assuming that the classname ends with "Spec"
  def name: String = getClass.getSimpleName.dropRight(4)
  implicit def marshaller: Marshaller[Employee]
  implicit def unmarshaller: Unmarshaller[Employee]

  "The " + name should {
    "provide unmarshalling support for a case class" in {
      HttpEntity(ContentTypes.`application/json`, Employee.json).as[Employee] === Right(Employee.simple)
    }
    "provide marshalling support for a case class" in {
      marshal(Employee.simple) === Right(HttpEntity(ContentTypes.`application/json`, Employee.json))
    }
    "use UTF-8 as the default charset for JSON source decoding" in {
      HttpEntity(MediaTypes.`application/json`, Employee.utf8json).as[Employee] === Right(Employee.utf8)
    }
    "provide proper error messages for requirement errors" in {
      val Left(MalformedContent(msg, Some(ex: IllegalArgumentException))) =
        HttpEntity(MediaTypes.`application/json`, Employee.illegalEmployeeJson).as[Employee]
      msg === "requirement failed: Board members must be older than 40"
    }
  }
}

class SprayJsonSupportSpec extends Specification with SprayJsonSupport with JsonSupportSpec {
  object MyJsonProtocol extends DefaultJsonProtocol {
    implicit val employeeFormat = jsonFormat5(Employee.apply)
  }
  import MyJsonProtocol._

  def marshaller: Marshaller[Employee] = sprayJsonMarshaller(implicitly[RootJsonWriter[Employee]], CompactPrinter)
  def unmarshaller: Unmarshaller[Employee] = sprayJsonUnmarshaller[Employee]
}

class Json4sSupportSpec extends Specification with Json4sSupport with JsonSupportSpec {
  val json4sFormats = DefaultFormats

  def marshaller: Marshaller[Employee] = json4sMarshaller[Employee]
  def unmarshaller: Unmarshaller[Employee] = json4sUnmarshaller[Employee]
}

class Json4sJacksonSupportSpec extends Specification with Json4sJacksonSupport with JsonSupportSpec {
  val json4sJacksonFormats = DefaultFormats

  def marshaller: Marshaller[Employee] = json4sMarshaller[Employee]
  def unmarshaller: Unmarshaller[Employee] = json4sUnmarshaller[Employee]
}

class LiftJsonSupportSpec extends Specification with LiftJsonSupport with JsonSupportSpec {
  val liftJsonFormats = net.liftweb.json.DefaultFormats

  def marshaller: Marshaller[Employee] = liftJsonMarshaller[Employee]
  def unmarshaller: Unmarshaller[Employee] = liftJsonUnmarshaller[Employee]
}

class PlayJsonSupportSpec extends Specification with PlayJsonSupport with JsonSupportSpec {
  implicit val employeeReader = Json.reads[Employee]
  implicit val employeeWriter = Json.writes[Employee]

  def marshaller: Marshaller[Employee] = playJsonMarshaller[Employee]
  def unmarshaller: Unmarshaller[Employee] = playJsonUnmarshaller[Employee]
}
