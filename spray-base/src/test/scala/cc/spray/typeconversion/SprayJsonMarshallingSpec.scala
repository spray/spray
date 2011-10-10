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

import json._
import http._
import MediaTypes._
import org.specs2.mutable.Specification

class SprayJsonMarshallingSpec extends Specification with SprayJsonSupport {

  case class Employee(fname: String, name: String, age: Int, id: Long, boardMember: Boolean) {
    require(!boardMember || age > 40, "Board members must be older than 40")
  }
  object MyJsonFormat extends DefaultJsonProtocol {
    implicit val employeeFormat = jsonFormat(Employee, "fname", "name", "age", "id", "boardMember")
  }
  import MyJsonFormat._

  val employeeA = Employee("Frank", "Smith", 42, 12345, false)
  val employeeAJson = PrettyPrinter(employeeA.toJson)

  "The SprayJsonSupport" should {
    "provide unmarshalling capability for case classes with an in-scope JsonFormat" in {
      HttpContent(`application/json`, employeeAJson).as[Employee] mustEqual
              Right(Employee("Frank", "Smith", 42, 12345, false))
    }
    "provide marshalling capability for case classes with an in-scope JsonFormat" in {
      marshall(employeeA)() mustEqual Right(HttpContent(ContentType(`application/json`), employeeAJson))
    }
  }

  def marshall[A :Marshaller](obj: A)(contentTypeSelector: ContentTypeSelector = Some(_)) = {
    marshaller[A].apply(contentTypeSelector) match {
      case MarshalWith(converter) => Right(converter(obj))
      case CantMarshal(onlyTo) => Left(onlyTo)
    }
  }
}