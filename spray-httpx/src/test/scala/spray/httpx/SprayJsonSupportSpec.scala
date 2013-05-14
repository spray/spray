/*
 * Copyright (C) 2011-2013 spray.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package spray.httpx

import org.specs2.mutable.Specification
import spray.json._
import spray.http._
import spray.httpx.unmarshalling._
import spray.httpx.marshalling._
import HttpCharsets._
import MediaTypes._

class SprayJsonSupportSpec extends Specification with SprayJsonSupport {

  case class Employee(fname: String, name: String, age: Int, id: Long, boardMember: Boolean) {
    require(!boardMember || age > 40, "Board members must be older than 40")
  }
  object MyJsonProtocol extends DefaultJsonProtocol {
    implicit val employeeFormat = jsonFormat(Employee, "fname", "name", "age", "id", "boardMember")
  }
  import MyJsonProtocol._

  val employee = Employee("Frank", "Smith", 42, 12345, false)
  val employeeJson = employee.toJson.prettyPrint

  "The SprayJsonSupport" should {
    "provide unmarshalling capability for case classes with an in-scope JsonFormat" in {
      HttpEntity(`application/json`, employeeJson).as[Employee] === Right(employee)
    }

    "provide marshalling capability for case classes with an in-scope JsonFormat" in {
      marshal(employee) === Right(HttpEntity(ContentType.`application/json`, employeeJson))
    }

    "using UTF-8 as the default charset for JSON source decoding" in {
      val json =
        """{
          |  "fname": "Fränk",
          |  "name": "Smi√",
          |  "age": 42,
          |  "id": 12345,
          |  "boardMember": false
          |}""".stripMargin.getBytes(HttpCharsets.`UTF-8`.nioCharset)
      HttpEntity(`application/json`, json).as[Employee] === Right(Employee("Fränk", "Smi√", 42, 12345, false))
    }
  }

}