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

  implicit def multipartContentMarshaller =
    Marshaller.of[MultipartContent](new `multipart/mixed`(randomBoundary)) { (value, contentType, ctx) ⇒
      val r = new ByteArrayRendering(512)
      val boundary = contentType.mediaType.asInstanceOf[MultipartMediaType].boundary
      if (!value.parts.isEmpty) {
        value.parts.foreach { part ⇒
          r ~~ '-' ~~ '-' ~~ boundary ~~ CrLf
          part.headers.foreach { header ⇒ if (header.isNot("content-type")) r ~~ header ~~ CrLf }
          part.entity match {
            case EmptyEntity       ⇒
            case HttpBody(ct, buf) ⇒ if (buf.length > 0) r ~~ `Content-Type` ~~ ct ~~ CrLf ~~ CrLf ~~ buf ~~ CrLf
          }
        }
        r ~~ '-' ~~ '-' ~~ boundary ~~ '-' ~~ '-'
        ctx.marshalTo(HttpEntity(contentType, r.get))
      } else ctx.marshalTo(EmptyEntity)
    }

  implicit def multipartFormDataMarshaller(implicit mcm: Marshaller[MultipartContent]) =
    Marshaller[MultipartFormData] { (value, ctx) ⇒
      ctx.tryAccept(`multipart/form-data`) match {
        case None ⇒ ctx.rejectMarshalling(Seq(`multipart/form-data`))
        case _ ⇒ mcm(
          value = MultipartContent {
            value.fields.map {
              case (name, part) ⇒ part.copy(
                headers = `Content-Disposition`("form-data", Map("name" -> name)) :: part.headers)
            }(collection.breakOut)
          },
          ctx = new DelegatingMarshallingContext(ctx) {
            var boundary = ""
            override def tryAccept(contentType: ContentType) = {
              boundary = contentType.mediaType.asInstanceOf[MultipartMediaType].boundary
              Some(contentType)
            }
            override def marshalTo(entity: HttpEntity): Unit = { ctx.marshalTo(overrideContentType(entity)) }
            override def startChunkedMessage(entity: HttpEntity, sentAck: Option[Any])(implicit sender: ActorRef) =
              ctx.startChunkedMessage(overrideContentType(entity), sentAck)
            def overrideContentType(entity: HttpEntity) =
              entity.flatMap(body ⇒ HttpEntity(new `multipart/form-data`(boundary), body.buffer))
          })
      }
    }
}

object MultipartMarshallers extends MultipartMarshallers