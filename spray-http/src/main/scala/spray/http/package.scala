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

package spray

import java.nio.charset.Charset
import spray.http.parser.HttpParser

package object http {
  val UTF8: Charset = HttpCharsets.`UTF-8`.nioCharset

  /**
   * Warms up the spray.http module by triggering the loading of most classes in this package,
   * so as to increase the speed of the first usage.
   */
  def warmUp() {
    HttpParser.parseHeaders {
      List(
        HttpHeaders.RawHeader("Accept", "*/*,text/plain,custom/custom"),
        HttpHeaders.RawHeader("Accept-Charset", "*,UTF-8"),
        HttpHeaders.RawHeader("Accept-Encoding", "gzip,custom"),
        HttpHeaders.RawHeader("Accept-Language", "*,de-de,custom"),
        HttpHeaders.RawHeader("Authorization", "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ=="),
        HttpHeaders.RawHeader("Cache-Control", "no-cache"),
        HttpHeaders.RawHeader("Connection", "close"),
        HttpHeaders.RawHeader("Content-Disposition", "form-data"),
        HttpHeaders.RawHeader("Content-Encoding", "deflate"),
        HttpHeaders.RawHeader("Content-Length", "42"),
        HttpHeaders.RawHeader("Content-Type", "application/json"),
        HttpHeaders.RawHeader("Cookie", "spray=cool"),
        HttpHeaders.RawHeader("Host", "spray.io"),
        HttpHeaders.RawHeader("X-Forwarded-For", "1.2.3.4"),
        HttpHeaders.RawHeader("Fancy-Custom-Header", "yeah"))
    }
    HttpResponse(status = 200)
  }
}

package http {

  case class ProductVersion(product: String = "", version: String = "", comment: String = "") {
    override def toString =
      if (version.isEmpty && comment.isEmpty) product
      else {
        val sb = new java.lang.StringBuilder(product)
        if (!version.isEmpty) sb.append('/').append(version)
        if (!comment.isEmpty) {
          if (sb.length > 0) sb.append(' ')
          sb.append('(').append(comment).append(')')
        }
        sb.toString
      }
  }

  object ProductVersion {
    def parseMultiple(string: String): Seq[ProductVersion] =
      parser.HttpParser.parse(HttpParser.ProductVersionComments, string) match {
        case Right(x)   ⇒ x
        case Left(info) ⇒ throw new IllegalArgumentException(s"'$string' is not a legal sequence of ProductVersions: ${info.formatPretty}")
      }
  }
}