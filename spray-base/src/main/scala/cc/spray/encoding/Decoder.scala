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
package encoding

import http._
import java.io._

trait Decoder {
  def encoding: HttpEncoding
  
  def decode[T <: HttpMessage[T]](message: T): T = message.content match {
    case Some(content) => message.withContent(
      content = Some(HttpContent(content.contentType, decodeBuffer(content.buffer)))
    )
    case _ => message
  }
  
  def decodeBuffer(buffer: Array[Byte]): Array[Byte]

  protected def copyBuffer(buffer: Array[Byte])(copy: (InputStream, OutputStream) => Unit) = {
    val in = new ByteArrayInputStream(buffer)
    val out = new ByteArrayOutputStream()
    copy(in, out)
    out.toByteArray
  }
}