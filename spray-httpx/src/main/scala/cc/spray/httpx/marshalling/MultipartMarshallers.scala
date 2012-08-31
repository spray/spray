/*
 * Copyright (C) 2011-2012 spray.cc
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

package cc.spray.httpx.marshalling

import java.util.Random
import java.io.ByteArrayOutputStream
import org.parboiled.common.Base64
import cc.spray.http._
import MediaTypes._
import HttpHeaders._


trait MultipartMarshallers {
  protected val multipartBoundaryRandom = new Random

  /**
   * Creates a new random 144-bit number and base64 encodes it (RFC2045, yielding 24 characters).
   */
  def randomBoundary = {
    val array = new Array[Byte](18)
    multipartBoundaryRandom.nextBytes(array)
    Base64.rfc2045.encodeToString(array, false)
  }

  implicit def multipartContentMarshaller =
    Marshaller[MultipartContent](new `multipart/mixed`(Some(randomBoundary))) { (value, contentType, ctx) =>
      val out = new ByteArrayOutputStream(1024)
      val boundary = contentType.mediaType.asInstanceOf[MultipartMediaType].boundary.get

      def putCrLf() { put('\r'); put('\n') }
      def putDashDash() { put('-'); put('-') }
      def put(char: Char) { out.write(char.asInstanceOf[Byte]) }
      def putHeader(name: String, value: String) { putString(name); put(':'); put(' '); putString(value); putCrLf() }
      def putString(string: String) {
        val chars = new Array[Char](string.length)
        string.getChars(0, string.length, chars, 0)
        var i = 0
        while (i < chars.length) { put(chars(i)); i += 1 }
      }

      if (!value.parts.isEmpty) {
        value.parts.foreach { part =>
          putDashDash(); putString(boundary); putCrLf()
          part.headers.foreach { header =>
            require(header.name != "Content-Type", "")
            putHeader(header.name, header.value)
          }
          part.entity.foreach { (ct, buf) =>
            if (buf.length > 0) {
              putHeader("Content-Type", ct.value)
              putCrLf()
              out.write(buf)
              putCrLf()
            }
          }
        }
        putDashDash(); putString(boundary); putDashDash()
        ctx.marshalTo(HttpBody(contentType, out.toByteArray))
      } else ctx.marshalTo(EmptyEntity)
    }

  implicit def multipartFormDataMarshaller(implicit mcm: Marshaller[MultipartContent]) =
    new Marshaller[MultipartFormData] {
      def apply(selector: ContentTypeSelector) = selector(`multipart/form-data`) match {
        case _: Some[_] =>
          var boundary: Option[String] = None
          mcm { ct =>
            boundary = ct.mediaType.asInstanceOf[MultipartMediaType].boundary
            Some(ct)
          }.right.map { marshalling =>
          new Marshalling[MultipartFormData] {
            def apply(value: MultipartFormData, ctx: MarshallingContext) {
              val mpc = MultipartContent {
                value.fields.map {
                  case (name, part) => part.copy(
                    headers = `Content-Disposition`("form-data", Map("name" -> name)) :: part.headers
                  )
                } (collection.breakOut)
              }
              marshalling(mpc, ctx.withContentTypeOverriding(new `multipart/form-data`(boundary)))
            }
          }
        }
        case None => Left(Seq(`multipart/form-data`))
      }
    }
}

object MultipartMarshallers extends MultipartMarshallers