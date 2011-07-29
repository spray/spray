/*
 * Copyright (C) 2011 Mathias Doenitz
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

package cc.spray
package marshalling

import http._
import HttpHeaders._
import MediaTypes._
import HttpCharsets._
import test.AbstractSprayTest
import json._

class SprayJsonMarshallingSpec extends AbstractSprayTest with SprayJsonMarshalling {

  case class Employee(fname: String, name: String, age: Int, id: Long, boardMember: Boolean) {
    require(!boardMember || age > 40, "Board members must be older than 40")
  }
  object MyJsonFormat extends DefaultJsonProtocol {
    implicit val employeeFormat = jsonFormat(Employee, "fname", "name", "age", "id", "boardMember")
  }
  import MyJsonFormat._

  val employeeA = Employee("Frank", "Smith", 42, 12345, false)
  val employeeAJson = PrettyPrinter(employeeA.toJson)

  "The SprayJsonMarshalling" should {
    "provide unmarshalling capability for case classes with an in-scope JsonFormat" in {
      test(HttpRequest(content = Some(HttpContent(`application/json`, employeeAJson)))) {
        content(as[Employee]) { echoComplete }
      }.response.content.as[String] mustEqual Right("Employee(Frank,Smith,42,12345,false)")
    }
    "provide marshalling capability for case classes with an in-scope JsonFormat" in {
      test(HttpRequest()) {
        _.complete(employeeA)
      }.response.content.as[String] mustEqual Right(employeeAJson)
    }
  }

}