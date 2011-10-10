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

abstract class UnmarshallerBase[A] extends Deserializer[HttpContent, A] {
  val canUnmarshalFrom: List[ContentTypeRange]

  def apply(content: HttpContent) = {
    if (canUnmarshalFrom.exists(_.matches(content.contentType))) unmarshal(content)
    else Left(UnsupportedContentType(canUnmarshalFrom))
  }

  protected def unmarshal(content: HttpContent): Either[DeserializationError, A]

  /**
   * Helper method for turning exceptions occuring during evaluation of the named parameter into
   * [[cc.spray.typeconversion.MalformedContent]] instances.
   */
  protected def protect(f: => A): Either[DeserializationError, A] = {
    try {
      Right(f)
    } catch {
      case e: Exception => Left(MalformedContent(e.getMessage))
    }
  }   
} 