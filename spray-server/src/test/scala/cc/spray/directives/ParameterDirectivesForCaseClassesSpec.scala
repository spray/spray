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
package directives

import http._
import test.AbstractSprayTest

class ParameterDirectivesForCaseClassesSpec extends AbstractSprayTest {

  case class Person(name: String, fname: String)
  case class Employee(fname: String, name: String, age: Int, id: Long, boardMember: Boolean) {
    require(!boardMember || age > 40, "Board members must be older than 40")
  }

  "The 'parameter' extraction for case classes directive" should {
    "extract a case class instance with 2 elements based on the corresponding required parameters" in {
      test(HttpRequest(uri = "/person?name=Parsons&FirstName=Ellen")) {
        path("person") {
          parameters("name", "FirstName").as(instanceOf(Person)) { person =>
            _.complete(person.toString)
          }
        }
      }.response.content.as[String] mustEqual Right("Person(Parsons,Ellen)")
    }
    "extract a case class instance with 5 elements based on the corresponding required parameters" in {
      test(HttpRequest(uri = "/employee?name=McCormick&firstname=Pete&board=yes&id=1234567&age=57")) {
        path("employee") {
          parameters('firstname, 'name, 'age, 'id, 'board).as(instanceOf(Employee)) { employee =>
            _.complete(employee.toString)
          }
        }
      }.response.content.as[String] mustEqual Right("Employee(Pete,McCormick,57,1234567,true)")
    }
    "create a proper Rejection for missing parameters" in {
      test(HttpRequest(uri = "/employee?name=McCormick&firstname=Pete&board=yes&age=57")) {
        path("employee") {
          parameters('firstname, 'name, 'age, 'id, 'board).as(instanceOf(Employee)) { employee =>
            _.complete(employee.toString)
          }
        }
      }.rejections mustEqual Set(MissingQueryParamRejection("id"))
    }
    "create a proper Rejection for malformed parameters" in {
      test(HttpRequest(uri = "/employee?name=McCormick&firstname=Pete&board=yes&id=12XY567&age=57")) {
        path("employee") {
          parameters('firstname, 'name, 'age, 'id, 'board).as(instanceOf(Employee)) { employee =>
            _.complete(employee.toString)
          }
        }
      }.rejections mustEqual Set(MalformedQueryParamRejection("'12XY567' is not a valid 64-bit integer value"))
    }
    "create a proper Rejection for failed custom validations" in {
      test(HttpRequest(uri = "/employee?name=McCormick&firstname=Pete&board=yes&id=1234567&age=37")) {
        path("employee") {
          parameters('firstname, 'name, 'age, 'id, 'board).as(instanceOf(Employee)) { employee =>
            _.complete(employee.toString)
          }
        }
      }.rejections mustEqual Set(MalformedQueryParamRejection("requirement failed: Board members must be older than 40"))
    }
  }

}