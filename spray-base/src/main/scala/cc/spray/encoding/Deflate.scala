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
import org.parboiled.common.FileUtils
import java.util.zip._

abstract class Deflate extends Decoder with Encoder {
  val encoding = HttpEncodings.deflate

  def decodeBuffer(buffer: Array[Byte]) = copyBuffer(buffer) { (in, out) =>
    FileUtils.copyAll(new InflaterInputStream(in), out)
  }

  def encodeBuffer(buffer: Array[Byte]) = copyBuffer(buffer) { (in, out) =>
    FileUtils.copyAll(in, new DeflaterOutputStream(out))
  }
}

/**
 * An encoder and decoder for the HTTP 'deflate' encoding.
 */
object Deflate extends Deflate { self =>

  def handle(message: HttpMessage[_]) =
    message.isInstanceOf[HttpRequest] || message.asInstanceOf[HttpResponse].status.isSuccess

  def apply(minContentSize: Int) = new Deflate {
    def handle(message: HttpMessage[_]) =
      self.handle(message) && message.content.isDefined && message.content.get.buffer.length >= minContentSize
  }

  def apply(predicate: HttpMessage[_] => Boolean) = new Deflate {
    def handle(message: HttpMessage[_]) = predicate(message)
  }
}