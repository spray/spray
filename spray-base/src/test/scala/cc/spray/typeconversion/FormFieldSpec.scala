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
import org.specs2.mutable.Specification
import xml.NodeSeq

class FormFieldSpec extends Specification with DefaultUnmarshallers with DefaultMarshallers with FromStringDeserializers {

  val formData = FormData(Map("surname" -> "Smith", "age" -> "42"))
  val multipartFormData = MultipartFormData(Map("surname" -> BodyPart("Smith"), "age" -> BodyPart(<int>42</int>)))

  "The FormField infrastructure" should {
    "properly allow access to the fields of www-urlencoded forms" in {
      formData.toHttpContent.formField("surname").right.get.as[String] mustEqual Right("Smith")
      formData.toHttpContent.formField("age").right.get.as[Int] mustEqual Right(42)
    }

    "properly allow access to the fields of www-urlencoded forms containing special chars" in {
      FormData(Map("name" -> "Smith&Wesson")).toHttpContent.formField("name").right.get.as[String] mustEqual
        Right("Smith&Wesson")
    }

    "properly allow access to the fields of multipart/form-data forms" in {
      multipartFormData.toHttpContent.formField("surname").right.get.as[String] mustEqual Right("Smith")
      multipartFormData.toHttpContent.formField("age").right.get.as[NodeSeq] mustEqual Right(<int>42</int>)
    }

    "return an error when accessing a field of www-urlencoded forms for which no FromStringOptionDeserializer is available" in {
      formData.toHttpContent.formField("age").right.get.as[NodeSeq] mustEqual
        Left(UnsupportedContentType("Field 'age' can only be read from 'multipart/form-data' form content"))
    }

    "return an error when accessing a field of multipart forms for which no Unmarshaller is available" in {
      multipartFormData.toHttpContent.formField("age").right.get.as[Int] mustEqual
        Left(UnsupportedContentType("Field 'age' can only be read from 'application/x-www-form-urlencoded' form content"))
    }
  }

}