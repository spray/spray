/*
 * Copyright (C) 2011-2012 spray.io
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

package spray.routing


class CaseClassExtractionSpec extends RoutingSpec {

  case class Age(years: Int)
  case class Employee(fname: String, name: String, age: Int, id: Long, boardMember: Boolean) {
    require(!boardMember || age > 40, "Board members must be older than 40")
  }

  "Value extraction as case class" should {
    "work for" in {
      "1 parameter case classes from string extractions" in {
        Get("/?age=42") ~> {
          parameter("age").as(Age) { echoComplete }
        } ~> check {
          entityAs[String] === "Age(42)"
        }
      }

      "1 parameter case classes from non-string extractions" in {
        Get("/?age=42") ~> {
          parameter("age".as[Int]).as(Age) { echoComplete }
        } ~> check { entityAs[String] === "Age(42)" }
      }

      "5 parameter case classes from string extractions" in {
        Get("/?name=McCormick&firstname=Pete&board=yes&id=1234567&age=57") ~> {
          parameters('firstname, 'name, 'age, 'id, 'board).as(Employee) { echoComplete }
        } ~> check { entityAs[String] === "Employee(Pete,McCormick,57,1234567,true)" }
      }

      "5 parameter case classes from mixed extractions" in {
        Get("/?name=McCormick&firstname=Pete&board=yes&id=1234567&age=57") ~> {
          parameters('firstname, 'name, 'age.as[Int], 'id ? 0, 'board).as(Employee) { echoComplete }
        } ~> check { entityAs[String] === "Employee(Pete,McCormick,57,1234567,true)" }
      }
    }

    "create a proper Rejection for" in {
      "missing parameters" in {
        Get("/?name=McCormick&firstname=Pete&board=yes&age=57") ~> {
          parameters('firstname, 'name, 'age, 'id, 'board).as(Employee) { echoComplete }
        } ~> check { rejection === MissingQueryParamRejection("id") }
      }

      "create a proper Rejection for malformed parameters" in {
        Get("/?name=McCormick&firstname=Pete&board=yes&id=12XY567&age=57") ~> {
          (parameters('firstname, 'name) & parameters('age, 'id, 'board.as[Boolean])).as(Employee) {
            echoComplete
          }
        } ~> check { rejection === ValidationRejection("'12XY567' is not a valid 64-bit integer value") }
      }

      "create a proper Rejection for failed custom validations" in {
        Get("/?name=McCormick&firstname=Pete&board=yes&id=1234567&age=37") ~> {
          parameters('firstname, 'name, 'age.as[Int], 'id, 'board).as(Employee) { echoComplete }
        } ~> check { rejection === ValidationRejection("requirement failed: Board members must be older than 40") }
      }
    }
  }

}