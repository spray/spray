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

package spray.httpx.marshalling

import java.nio.CharBuffer
import xml.NodeSeq
import spray.http._
import MediaTypes._


trait BasicMarshallers {

  def byteArrayMarshaller(contentType: ContentType) =
    Marshaller.of[Array[Byte]](contentType) { (value, ct, ctx) =>
      ctx.marshalTo(HttpBody(ct, value))
    }

  implicit val ByteArrayMarshaller = byteArrayMarshaller(ContentType.`application/octet-stream`)

  implicit val CharArrayMarshaller =
    Marshaller.of[Array[Char]](ContentType.`text/plain`) { (value, contentType, ctx) =>
      ctx.marshalTo {
        if (value.length > 0) {
          val nioCharset = contentType.charset.nioCharset
          val charBuffer = CharBuffer.wrap(value)
          val byteBuffer = nioCharset.encode(charBuffer)
          HttpBody(contentType, byteBuffer.array)
        } else EmptyEntity
      }
    }

  //# string-marshaller
  implicit val StringMarshaller =
    Marshaller.of[String](ContentType.`text/plain`) { (value, contentType, ctx) =>
      ctx.marshalTo(if (value.isEmpty) EmptyEntity else HttpBody(contentType, value))
    }
  //#

  //# nodeseq-marshaller
  implicit val NodeSeqMarshaller =
    Marshaller.delegate[NodeSeq, String](
      `text/xml`, `text/html`, `application/xhtml+xml`
    )(_.toString)
  //#

  implicit val FormDataMarshaller =
    Marshaller.delegate[FormData, String](`application/x-www-form-urlencoded`) { (formData, contentType) =>
      import java.net.URLEncoder.encode
      val charset = contentType.charset.value
      formData.fields.map { case (key, value) => encode(key, charset) + '=' + encode(value, charset) }.mkString("&")
    }

  implicit val ThrowableMarshaller = Marshaller[Throwable] { (value, ctx) => ctx.handleError(value) }

  implicit val StatusCodeMarshaller = Marshaller[StatusCode] { (value, ctx) =>
    value match {
      case StatusCodes.NoContent => ctx.marshalTo(EmptyEntity)
      case status => ctx.tryAccept(ContentType.`text/plain`) match {
        case Some(contentType) => ctx.marshalTo(HttpBody(contentType, status.defaultMessage))
        case None => ctx.rejectMarshalling(Seq(ContentType.`text/plain`))
      }
    }
  }

  implicit val HttpEntityMarshaller = Marshaller[HttpEntity] { (value, ctx) =>
    value match {
      case EmptyEntity => ctx.marshalTo(EmptyEntity)
      case body@ HttpBody(contentType, _) => ctx.tryAccept(contentType) match {
        case Some(_) => ctx.marshalTo(body) // we do NOT use the accepted CT here, since we do not want to recode
        case None => ctx.rejectMarshalling(Seq(contentType))
      }
    }
  }
}

object BasicMarshallers extends BasicMarshallers