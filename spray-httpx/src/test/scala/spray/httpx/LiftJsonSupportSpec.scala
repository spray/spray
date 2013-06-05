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

package spray.httpx

import org.specs2.mutable.Specification
import net.liftweb.json.DefaultFormats
import spray.http._
import spray.httpx.unmarshalling._
import spray.httpx.marshalling._
import MediaTypes._


//needs to be defined at top level to avoid some test failures due to how lift-json works
case class Employee(fname: String, name: String, age: Int, id: Long, boardMember: Boolean)

class LiftJsonSupportSpec extends Specification with LiftJsonSupport {

  val liftJsonFormats = DefaultFormats

  val employee = Employee("Frank", "Smith", 42, 12345, false)
  val employeeJson = """{"fname":"Frank","name":"Smith","age":42,"id":12345,"boardMember":false}"""

  "The LiftJsonSupport" should {
    "provide unmarshalling support for a case class" in {
      HttpBody(`application/json`, employeeJson).as[Employee] === Right(employee)
    }
    "provide marshalling support for a case class" in {
      marshal(employee) == Right(HttpBody(`application/json`, employeeJson))
    }
  }

}