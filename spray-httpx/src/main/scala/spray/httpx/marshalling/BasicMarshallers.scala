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

package spray.httpx.marshalling

import java.nio.CharBuffer
import scala.xml.NodeSeq
import spray.http._
import MediaTypes._

trait BasicMarshallers {

  def byteArrayMarshaller(contentType: ContentType) =
    Marshaller.of[Array[Byte]](contentType) { (value, _, ctx) ⇒
      // we marshal to the ContentType given as argument to the method, not the one established by content-negotiation,
      // since the former is the one belonging to the byte array
      ctx.marshalTo(HttpEntity(contentType, value))
    }

  implicit val ByteArrayMarshaller = byteArrayMarshaller(ContentTypes.`application/octet-stream`)

  implicit val CharArrayMarshaller =
    Marshaller.of[Array[Char]](ContentTypes.`text/plain(UTF-8)`) { (value, contentType, ctx) ⇒
      ctx.marshalTo {
        if (value.length > 0) {
          val charBuffer = CharBuffer.wrap(value)
          val byteBuffer = contentType.charset.nioCharset.encode(charBuffer)
          val array = new Array[Byte](byteBuffer.remaining())
          byteBuffer.get(array)
          HttpEntity(contentType, array)
        } else EmptyEntity
      }
    }

  //# string-marshaller
  def stringMarshaller(charset: HttpCharset, more: HttpCharset*): Marshaller[String] =
    stringMarshaller(ContentType(`text/plain`, charset), more map (ContentType(`text/plain`, _)): _*)

  def stringMarshaller(contentType: ContentType, more: ContentType*): Marshaller[String] =
    Marshaller.of[String](contentType +: more: _*) { (value, contentType, ctx) ⇒
      ctx.marshalTo(HttpEntity(contentType, value))
    }

  // prefer UTF-8 encoding, but also render with other encodings if the client requests them
  implicit val StringMarshaller = stringMarshaller(ContentTypes.`text/plain(UTF-8)`, ContentTypes.`text/plain`)
  //#

  //# nodeseq-marshaller
  implicit val NodeSeqMarshaller =
    Marshaller.delegate[NodeSeq, String](`text/xml`, `application/xml`,
      `text/html`, `application/xhtml+xml`)(_.toString)
  //#

  implicit val FormDataMarshaller =
    Marshaller.delegate[FormData, String](`application/x-www-form-urlencoded`) { (formData, contentType) ⇒
      import java.net.URLEncoder.encode
      val charset = contentType.charset.value
      formData.fields.map { case (key, value) ⇒ encode(key, charset) + '=' + encode(value, charset) }.mkString("&")
    }

  implicit val ThrowableMarshaller = Marshaller[Throwable] { (value, ctx) ⇒ ctx.handleError(value) }

  implicit val HttpEntityMarshaller = Marshaller[HttpEntity] { (value, ctx) ⇒
    value match {
      case EmptyEntity ⇒ ctx.marshalTo(EmptyEntity)
      case body @ HttpBody(contentType, _) ⇒ ctx.tryAccept(contentType :: Nil) match {
        case Some(_) ⇒ ctx.marshalTo(body) // we do NOT use the accepted CT here, since we do not want to recode
        case None    ⇒ ctx.rejectMarshalling(Seq(contentType))
      }
    }
  }
}

object BasicMarshallers extends BasicMarshallers