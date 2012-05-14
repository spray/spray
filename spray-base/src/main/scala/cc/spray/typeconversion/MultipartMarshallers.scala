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

package cc.spray
package typeconversion

import util._
import http._
import MediaTypes._
import HttpHeaders._
import java.util.Random
import org.parboiled.common.Base64
import java.io.ByteArrayOutputStream

trait MultipartMarshallers {
  protected lazy val multipartBoundaryRandom = new Random()

  /**
   * Creates a new random 144-bit number and base64 encodes it (RFC2045, yielding 24 characters).
   */
  def randomBoundary = {
    Base64.rfc2045.encodeToString(make(new Array[Byte](18)) { multipartBoundaryRandom.nextBytes(_) }, false)
  }

  implicit val MultipartContentMarshaller = new SimpleMarshaller[MultipartContent] {
    def canMarshalTo = ContentType(new `multipart/mixed`(Some(randomBoundary))) :: Nil

    def marshal(value: MultipartContent, contentType: ContentType) = {
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
          part.content.foreach { content =>
            if (content.buffer.length > 0) {
              putHeader("Content-Type", content.contentType.value)
              putCrLf()
              out.write(content.buffer)
              putCrLf();
            }
          }
        }
        putDashDash(); putString(boundary); putDashDash()
      }
      HttpContent(contentType, out.toByteArray)
    }
  }

  implicit val MultipartFormDataMarshaller = new SimpleMarshaller[MultipartFormData] {
    def canMarshalTo = ContentType(new `multipart/form-data`(Some(randomBoundary))) :: Nil

    def marshal(value: MultipartFormData, contentType: ContentType) = {
      MultipartContentMarshaller.marshal(
        value = MultipartContent {
          value.parts.map {
            case (name, part) => part.copy(
              headers = `Content-Disposition`("form-data", Map("name" -> name)) :: part.headers
            )
          } (collection.breakOut)
        },
        contentType = contentType
      )
    }
  }

}

object MultipartMarshallers extends MultipartMarshallers