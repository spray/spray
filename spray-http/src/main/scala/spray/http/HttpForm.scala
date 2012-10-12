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

package spray.http


sealed trait HttpForm {
  type FieldType
  def fields: Map[String, FieldType]
}

/**
 * Model for `application/x-www-form-urlencoded` form data.
 */
case class FormData(fields: Map[String, String]) extends HttpForm {
  type FieldType = String
}

object FormData {
  val Empty = FormData(Map.empty)
}


/**
 * Model for `multipart/form-data` content as defined in RFC 2388.
 */
case class MultipartFormData(fields: Map[String, BodyPart]) extends HttpForm {
  type FieldType = BodyPart
}

object MultipartFormData {
  val Empty = MultipartFormData(Map.empty)
}