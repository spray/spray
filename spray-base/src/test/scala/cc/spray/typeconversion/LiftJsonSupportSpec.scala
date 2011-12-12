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
package typeconversion

import http._
import MediaTypes._
import org.specs2.Specification
import net.liftweb.json.DefaultFormats

//needs to be defined at top level to avoid some test failures due to how lift-json works
case class Employee(fname: String, name: String, age: Int, id: Long, boardMember: Boolean)

class LiftJsonSupportSpec extends Specification with LiftJsonSupport { def is =

  "The LiftJsonSupport should" ^
  "provide unmarshalling support for a case class" ! unmarshallTest ^
  "provide marshalling support for a case class" ! marshallTest

  val liftJsonFormats = DefaultFormats

  val employeeA = Employee("Frank", "Smith", 42, 12345, false)
  val employeeAJson = """{"fname":"Frank","name":"Smith","age":42,"id":12345,"boardMember":false}"""

  def unmarshallTest = {
    HttpContent(`application/json`, employeeAJson).as[Employee] mustEqual
      Right(Employee("Frank", "Smith", 42, 12345, false))
  }

  def marshallTest = employeeA.toHttpContent mustEqual HttpContent(ContentType(`application/json`), employeeAJson)

}