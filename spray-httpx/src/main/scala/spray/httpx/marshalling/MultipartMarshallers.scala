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

package spray.httpx.marshalling

import java.util.Random
import org.parboiled.common.Base64
import akka.actor.ActorRef
import spray.http._
import MediaTypes._
import HttpHeaders._
import Rendering.CrLf

trait MultipartMarshallers {
  protected val multipartBoundaryRandom = new Random

  /**
   * Creates a new random 144-bit number and base64 encodes it (using a custom "safe" alphabet, yielding 24 characters).
   */
  def randomBoundary = {
    val array = new Array[Byte](18)
    multipartBoundaryRandom.nextBytes(array)
    Base64.custom.encodeToString(array, false)
  }

  implicit def multipartByterangesMarshaller = multipartPartsMarshaller[MultipartByteRanges](`multipart/byteranges`)
  implicit def multipartContentMarshaller = multipartPartsMarshaller[MultipartContent](`multipart/mixed`)

  private def multipartPartsMarshaller[T <: MultipartParts](mediatype: MultipartMediaType): Marshaller[T] = {
    val boundary = randomBoundary
    Marshaller.of[T](mediatype withBoundary boundary) { (value, contentType, ctx) ⇒
      if (!value.parts.isEmpty) {
        val r = new HttpDataRendering(rawBytesSizeHint = 512)
        value.parts.foreach { part ⇒
          r ~~ '-' ~~ '-' ~~ boundary ~~ CrLf
          part.headers.foreach { header ⇒ if (header.isNot("content-type")) r ~~ header ~~ CrLf }
          part.entity match {
            case HttpEntity.Empty              ⇒
            case HttpEntity.NonEmpty(ct, data) ⇒ r ~~ `Content-Type` ~~ ct ~~ CrLf ~~ CrLf ~~ data ~~ CrLf
          }
        }
        r ~~ '-' ~~ '-' ~~ boundary ~~ '-' ~~ '-'
        ctx.marshalTo(HttpEntity(contentType, r.get))
      } else ctx.marshalTo(HttpData.Empty)
    }
  }

  implicit def multipartFormDataMarshaller(implicit mcm: Marshaller[MultipartContent]) =
    Marshaller[MultipartFormData] { (value, ctx) ⇒
      ctx.tryAccept(`multipart/form-data` :: Nil) match {
        case None ⇒ ctx.rejectMarshalling(Seq(`multipart/form-data`))
        case _ ⇒ mcm(
          value = MultipartContent(value.fields),
          ctx = new DelegatingMarshallingContext(ctx) {
            var boundary = ""
            override def tryAccept(contentTypes: Seq[ContentType]) = {
              boundary = contentTypes.head.mediaType.parameters("boundary")
              contentTypes.headOption
            }
            override def marshalTo(entity: HttpEntity, headers: HttpHeader*): Unit =
              ctx.marshalTo(overrideContentType(entity), headers: _*)
            override def startChunkedMessage(entity: HttpEntity, sentAck: Option[Any], headers: Seq[HttpHeader])(implicit sender: ActorRef) =
              ctx.startChunkedMessage(overrideContentType(entity), sentAck, headers)
            def overrideContentType(entity: HttpEntity) =
              entity.flatMap(body ⇒ HttpEntity(`multipart/form-data` withBoundary boundary, body.data))
          })
      }
    }
}

object MultipartMarshallers extends MultipartMarshallers