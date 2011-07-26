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

import http._
import test.AbstractSprayTest

class CaseClassExtractionDirectivesSpec extends AbstractSprayTest {

  case class Age(years: Int)
  case class Employee(fname: String, name: String, age: Int, id: Long, boardMember: Boolean) {
    require(!boardMember || age > 40, "Board members must be older than 40")
  }

  "Value extraction as case class" should {
    "work for 1 parameter case classes from string extractions" in {
      test(HttpRequest(uri = "/?age=42")) {
        parameters("age").as(instanceOf(Age)) { age =>
          _.complete(age.toString)
        }
      }.response.content.as[String] mustEqual Right("Age(42)")
    }
    "work for 1 parameter case classes from non-string extractions" in {
      test(HttpRequest(uri = "/?age=42")) {
        parameters("age".as[Int]).as(instanceOf(Age)) { age =>
          _.complete(age.toString)
        }
      }.response.content.as[String] mustEqual Right("Age(42)")
    }
    "work for 5 parameter case classes from string extractions" in {
      test(HttpRequest(uri = "/?name=McCormick&firstname=Pete&board=yes&id=1234567&age=57")) {
        parameters('firstname, 'name, 'age, 'id, 'board).as(instanceOf(Employee)) { employee =>
          _.complete(employee.toString)
        }
      }.response.content.as[String] mustEqual Right("Employee(Pete,McCormick,57,1234567,true)")
    }
    "work for 5 parameter case classes from mixed extractions" in {
      test(HttpRequest(uri = "/?name=McCormick&firstname=Pete&board=yes&id=1234567&age=57")) {
        parameters('firstname, 'name, 'age.as[Int], 'id ? 0, 'board).as(instanceOf(Employee)) { employee =>
          _.complete(employee.toString)
        }
      }.response.content.as[String] mustEqual Right("Employee(Pete,McCormick,57,1234567,true)")
    }
    "create a proper Rejection for missing parameters" in {
      test(HttpRequest(uri = "/?name=McCormick&firstname=Pete&board=yes&age=57")) {
        parameters('firstname, 'name, 'age, 'id, 'board).as(instanceOf(Employee)) { employee =>
          _.complete(employee.toString)
        }
      }.rejections mustEqual Set(MissingQueryParamRejection("id"))
    }
    "create a proper Rejection for malformed parameters" in {
      test(HttpRequest(uri = "/?name=McCormick&firstname=Pete&board=yes&id=12XY567&age=57")) {
        (parameters('firstname, 'name) & parameters('age, 'id, 'board.as[Boolean])).as(instanceOf(Employee)) { employee =>
          _.complete(employee.toString)
        }
      }.rejections mustEqual Set(ValidationRejection("'12XY567' is not a valid 64-bit integer value"))
    }
    "create a proper Rejection for failed custom validations" in {
      test(HttpRequest(uri = "/?name=McCormick&firstname=Pete&board=yes&id=1234567&age=37")) {
        parameters('firstname, 'name, 'age.as[Int], 'id, 'board).as(instanceOf(Employee)) { employee =>
          _.complete(employee.toString)
        }
      }.rejections mustEqual Set(ValidationRejection("requirement failed: Board members must be older than 40"))
    }
  }

}