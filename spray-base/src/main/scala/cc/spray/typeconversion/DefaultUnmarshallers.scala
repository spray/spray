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

import utils._
import http._
import MediaTypes._
import MediaRanges._
import HttpCharsets._
import xml.{XML, NodeSeq}
import java.nio.ByteBuffer
import java.net.URLDecoder.decode
import java.io.ByteArrayInputStream

trait DefaultUnmarshallers extends MultipartUnmarshallers {
  
  implicit val StringUnmarshaller = new Deserializer[HttpContent, String] {
    def apply(content: HttpContent) = Right {
      // we can convert anything to a String
      new String(content.buffer, content.contentType.charset.getOrElse(`ISO-8859-1`).nioCharset)
    }
  }

  implicit val CharArrayUnmarshaller = new Deserializer[HttpContent, Array[Char]] {
    def apply(content: HttpContent) = Right { // we can convert anything to a char array
      val nioCharset = content.contentType.charset.getOrElse(`ISO-8859-1`).nioCharset
      val byteBuffer = ByteBuffer.wrap(content.buffer)
      val charBuffer = nioCharset.decode(byteBuffer)
      charBuffer.array()
    }
  }
  
  implicit val NodeSeqUnmarshaller = new SimpleUnmarshaller[NodeSeq] {
    val canUnmarshalFrom = ContentTypeRange(`text/xml`) ::
                           ContentTypeRange(`text/html`) ::
                           ContentTypeRange(`application/xhtml+xml`) :: Nil

    def unmarshal(content: HttpContent) = protect {
      if (content.contentType.charset.isDefined) {
        XML.loadString(StringUnmarshaller(content).right.get)
      } else {
        XML.load(new ByteArrayInputStream(content.buffer))
      }
    }
  }

  implicit val FormDataUnmarshaller = new SimpleUnmarshaller[FormData] {
    val canUnmarshalFrom = ContentTypeRange(`application/x-www-form-urlencoded`) :: Nil
  
    def unmarshal(content: HttpContent) = protect {
      FormData {
        val data = DefaultUnmarshallers.StringUnmarshaller(content).right.get
        val charset = content.contentType.charset.getOrElse(`ISO-8859-1`).aliases.head
        data.fastSplit('&').map {
          _.fastSplit('=') match {
            case key :: value :: Nil => (decode(key, charset), decode(value, charset))
            case _ => throw new IllegalArgumentException("'" + data + "' is not a valid form content")
          }
        } (collection.breakOut)
      }
    }
  }
}

object DefaultUnmarshallers extends DefaultUnmarshallers