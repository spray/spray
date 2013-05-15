package spray.httpx

import marshalling._
import spray.httpx.unmarshalling._
import spray.httpx.marshalling._
import org.specs2.mutable.Specification
import org.json4s.DefaultFormats
import spray.http.HttpEntity
import spray.http.MediaTypes
import spray.http.ContentType._
import spray.http.ContentType

case class Employee2(fname: String, name: String, age: Int, id: Long, boardMember: Boolean)

class Json4sSupportSpec extends Specification with Json4sSupport {

  val formats = DefaultFormats

  val employee = Employee2("Frank", "Smith", 42, 12345, false)
  val employeeJson = """{"fname":"Frank","name":"Smith","age":42,"id":12345,"boardMember":false}"""

  "The Json4sSupport" should {
    "provide unmarshalling support for a case class" in {
      HttpEntity(`application/json`, employeeJson).as[Employee2] === Right(employee)
    }
    "provide marshalling support for a case class" in {
      marshal(employee) === Right(HttpEntity(`application/json`, employeeJson))
    }
  }
}

class Json4sJacksonSupportSpec extends Specification with Json4sJacksonSupport {

  val formats = DefaultFormats

  val employee = Employee2("Frank", "Smith", 42, 12345, false)
  val employeeJson = """{"fname":"Frank","name":"Smith","age":42,"id":12345,"boardMember":false}"""

  "The Json4sJackosonSupport" should {
    "provide unmarshalling support for a case class" in {
      HttpEntity(`application/json`, employeeJson).as[Employee2] === Right(employee)
    }
    "provide marshalling support for a case class" in {
      marshal(employee) === Right(HttpEntity(`application/json`, employeeJson))
    }
  }
}
