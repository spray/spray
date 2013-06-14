/*
 * Copyright (C) 2011-2013 spray.io
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

package spray.httpx.unmarshalling

import java.nio.ByteBuffer
import java.io.{ InputStreamReader, ByteArrayInputStream }
import scala.xml.{ XML, NodeSeq }
import spray.util._
import spray.http._
import MediaTypes._

trait BasicUnmarshallers {

  implicit val ByteArrayUnmarshaller = new Unmarshaller[Array[Byte]] {
    def apply(entity: HttpEntity) = Right(entity.buffer)
  }

  implicit val CharArrayUnmarshaller = new Unmarshaller[Array[Char]] {
    def apply(entity: HttpEntity) = Right { // we can convert anything to a char array
      entity match {
        case HttpBody(contentType, buffer) ⇒
          contentType.charset.nioCharset.decode(ByteBuffer.wrap(buffer)).array()
        case EmptyEntity ⇒ Array.empty[Char]
      }
    }
  }

  implicit val StringUnmarshaller = new Unmarshaller[String] {
    def apply(entity: HttpEntity) = Right(entity.asString)
  }

  //# nodeseq-unmarshaller
  implicit val NodeSeqUnmarshaller =
    Unmarshaller[NodeSeq](`text/xml`, `application/xml`, `text/html`, `application/xhtml+xml`) {
      case HttpBody(contentType, buffer) ⇒
        XML.load(new InputStreamReader(new ByteArrayInputStream(buffer), contentType.charset.nioCharset))
      case EmptyEntity ⇒ NodeSeq.Empty
    }
  //#

  implicit val FormDataUnmarshaller =
    Unmarshaller[FormData](`application/x-www-form-urlencoded`) {
      case HttpBody(contentType, buffer) ⇒ FormData {
        val data = buffer.asString(contentType.charset.nioCharset)
        val charset = contentType.charset.value
        data.fastSplit('&').flatMap {
          case "" ⇒ Nil
          case string ⇒ string.fastSplit('=') match {
            case key :: value :: Nil ⇒
              import java.net.URLDecoder.decode
              Some(decode(key, charset) -> decode(value, charset))
            case _ ⇒ throw new IllegalArgumentException("'" + data + "' is not a valid form content: '" +
              string + "' does not constitute a valid key=value pair")
          }
        }(collection.breakOut)
      }
      case EmptyEntity ⇒ FormData.Empty
    }
}

object BasicUnmarshallers extends BasicUnmarshallers
