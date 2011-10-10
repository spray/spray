/*
 * Copyright (C) 2011 Mathias Doenitz
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team (http://github.com/jdegoes/blueeyes)
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
package http

import typeconversion._

/**
 * Basic model for multipart content as defind in RFC 2046.
 * If you are looking for a model for `multipart/form-data` you should be using [[cc.spray.http.MultipartFormData]].
 */
case class MultipartContent(parts: Seq[BodyPart])

/**
 * Model for `multipart/form-data` content as defined in RFC 2388.
 */
case class MultipartFormData(parts: Map[String, BodyPart])

/**
 * Model for one part of a multipart message.
 */
case class BodyPart(headers: List[HttpHeader], content: Option[HttpContent])

object BodyPart {
  def apply[A :Marshaller](value: A): BodyPart = apply(value.toHttpContent)
  def apply(content: HttpContent): BodyPart = apply(Nil, content)
  def apply(headers: List[HttpHeader], content: HttpContent): BodyPart = new BodyPart(headers, Some(content))
}

/**
 * Model for `application/x-www-form-urlencoded` form data.
 */
case class FormData(fields: Map[String, String])