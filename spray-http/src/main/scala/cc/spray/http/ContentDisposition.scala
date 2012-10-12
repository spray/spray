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

sealed abstract class ContentDisposition {
  def value: String
  override def toString = "ContentDisposition(" + value + ')'
}

// see http://tools.ietf.org/html/rfc2183
object ContentDispositions extends ObjectRegistry[String, ContentDisposition] {
  
  def register(disposition: ContentDisposition): ContentDisposition = {
    register(disposition, disposition.value.toLowerCase)
    disposition
  }
  
  private class PredefDisposition(val value: String) extends ContentDisposition

  val attachment  = register(new PredefDisposition("attachment"))
  val inline      = register(new PredefDisposition("inline"))
  val `form-data` = register(new PredefDisposition("form-data"))

  case class CustomContentDisposition(value: String) extends ContentDisposition
}