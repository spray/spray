/*
 * Copyright © 2011-2013 the spray project <http://spray.io>
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
import spray.http._
import MediaTypes._

trait BasicUnmarshallers {

  implicit val ByteArrayUnmarshaller = new Unmarshaller[Array[Byte]] {
    def apply(entity: HttpEntity) = Right(entity.data.toByteArray)
  }

  implicit val CharArrayUnmarshaller = new Unmarshaller[Array[Char]] {
    def apply(entity: HttpEntity) = Right { // we can convert anything to a char array
      entity match {
        case HttpEntity.NonEmpty(contentType, data) ⇒
          val charBuffer = contentType.charset.nioCharset.decode(ByteBuffer.wrap(data.toByteArray))
          val array = new Array[Char](charBuffer.length())
          charBuffer.get(array)
          array
        case HttpEntity.Empty ⇒ Array.empty[Char]
      }
    }
  }

  implicit val StringUnmarshaller = new Unmarshaller[String] {
    def apply(entity: HttpEntity) = Right(entity.asString)
  }

  //# nodeseq-unmarshaller
  implicit val NodeSeqUnmarshaller =
    Unmarshaller[NodeSeq](`text/xml`, `application/xml`, `text/html`, `application/xhtml+xml`) {
      case HttpEntity.NonEmpty(contentType, data) ⇒
        XML.load(new InputStreamReader(new ByteArrayInputStream(data.toByteArray), contentType.charset.nioCharset))
      case HttpEntity.Empty ⇒ NodeSeq.Empty
    }
  //#

  implicit val FormDataUnmarshaller =
    Unmarshaller[FormData](`application/x-www-form-urlencoded`) {
      case HttpEntity.Empty ⇒ FormData.Empty
      case entity: HttpEntity.NonEmpty ⇒
        val data = entity.asString
        try {
          val query = Uri.Query(data, entity.contentType.charset.nioCharset)
          FormData(query.toMap)
        } catch {
          case ex: IllegalUriException ⇒
            throw new IllegalArgumentException(ex.info.formatPretty.replace("Query,", "form content,"))
        }
    }
}

object BasicUnmarshallers extends BasicUnmarshallers
