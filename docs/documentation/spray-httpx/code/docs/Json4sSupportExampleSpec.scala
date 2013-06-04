package docs

import org.json4s.DefaultFormats
import org.specs2.mutable.Specification
import spray.httpx.Json4sSupport
import spray.httpx.marshalling._
import spray.http._

case class Employee2(fname: String, name: String, age: Int, id: Long, boardMember: Boolean)

class Json4sSupportExampleSpec extends Specification  {

  object Json4sMarshaller extends Json4sSupport {
    val json4sFormats = DefaultFormats
  }
  import spray.httpx.marshalling.{ Marshaller, MetaMarshallers }
  import spray.httpx.unmarshalling._

  import Json4sMarshaller._

  val employee = Employee2("Frank", "Smith", 42, 12345, false)
  val employeeJson = """{"fname":"Frank","name":"Smith","age":42,"id":12345,"boardMember":false}"""

  "The Json4sSupport" should {
    "provide unmarshalling support for a case class" in {
      HttpEntity(ContentTypes.`application/json`, employeeJson).as[Employee2] === Right(employee)
    }
    "provide marshalling support for a case class" in {
      marshal(employee) === Right(HttpEntity(ContentTypes.`application/json`, employeeJson))
    }
  }
}
