/*
 * Copyright © 2011-2015 the spray project <http://spray.io>
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

package spray.http

import org.specs2.mutable.Specification
import org.specs2.execute.{ Failure, FailureException }
import spray.http.parser.HttpParser
import HttpHeaders._
import MediaTypes._
import HttpCharsets._

class ContentNegotiationSpec extends Specification {

  "Content Negotiation" should {
    "work properly for requests with header(s)" in {

      "(without headers)" ! test { accept ⇒
        accept(`text/plain`) must select(`text/plain`, `UTF-8`)
        accept(`text/plain` withCharset `UTF-16`) must select(`text/plain`, `UTF-16`)
      }

      "Accept: */*" ! test { accept ⇒
        accept(`text/plain`) must select(`text/plain`, `UTF-8`)
        accept(`text/plain` withCharset `UTF-16`) must select(`text/plain`, `UTF-16`)
      }

      "Accept: */*;q=.8" ! test { accept ⇒
        accept(`text/plain`) must select(`text/plain`, `UTF-8`)
        accept(`text/plain` withCharset `UTF-16`) must select(`text/plain`, `UTF-16`)
      }

      "Accept: text/*" ! test { accept ⇒
        accept(`text/plain`) must select(`text/plain`, `UTF-8`)
        accept(`text/xml` withCharset `UTF-16`) must select(`text/xml`, `UTF-16`)
        accept(`audio/ogg`) must reject
      }

      "Accept: text/*;q=.8" ! test { accept ⇒
        accept(`text/plain`) must select(`text/plain`, `UTF-8`)
        accept(`text/xml` withCharset `UTF-16`) must select(`text/xml`, `UTF-16`)
        accept(`audio/ogg`) must reject
      }

      "Accept: text/*;q=0" ! test { accept ⇒
        accept(`text/plain`) must reject
        accept(`text/xml` withCharset `UTF-16`) must reject
        accept(`audio/ogg`) must reject
      }

      "Accept-Charset: UTF-16" ! test { accept ⇒
        accept(`text/plain`) must select(`text/plain`, `UTF-16`)
        accept(`text/plain` withCharset `UTF-8`) must reject
      }

      "Accept-Charset: UTF-16, UTF-8" ! test { accept ⇒
        accept(`text/plain`) must select(`text/plain`, `UTF-8`)
        accept(`text/plain` withCharset `UTF-16`) must select(`text/plain`, `UTF-16`)
      }

      "Accept-Charset: UTF-8;q=.2, UTF-16" ! test { accept ⇒
        accept(`text/plain`) must select(`text/plain`, `UTF-16`)
        accept(`text/plain` withCharset `UTF-8`) must select(`text/plain`, `UTF-8`)
      }

      "Accept-Charset: UTF-8;q=.2" ! test { accept ⇒
        accept(`text/plain`) must select(`text/plain`, `ISO-8859-1`)
        accept(`text/plain` withCharset `UTF-8`) must select(`text/plain`, `UTF-8`)
      }

      "Accept-Charset: latin1;q=.1, UTF-8;q=.2" ! test { accept ⇒
        accept(`text/plain`) must select(`text/plain`, `UTF-8`)
        accept(`text/plain` withCharset `UTF-8`) must select(`text/plain`, `UTF-8`)
      }

      "Accept-Charset: *" ! test { accept ⇒
        accept(`text/plain`) must select(`text/plain`, `UTF-8`)
        accept(`text/plain` withCharset `UTF-16`) must select(`text/plain`, `UTF-16`)
      }

      "Accept-Charset: *;q=0" ! test { accept ⇒
        accept(`text/plain`) must reject
        accept(`text/plain` withCharset `UTF-16`) must reject
      }

      "Accept-Charset: us;q=0.1,*;q=0" ! test { accept ⇒
        accept(`text/plain`) must select(`text/plain`, `US-ASCII`)
        accept(`text/plain` withCharset `UTF-8`) must reject
      }

      "Accept: text/xml, text/html;q=.5" ! test { accept ⇒
        accept(`text/plain`) must reject
        accept(`text/xml`) must select(`text/xml`, `UTF-8`)
        accept(`text/html`) must select(`text/html`, `UTF-8`)
        accept(`text/html`, `text/xml`) must select(`text/xml`, `UTF-8`)
        accept(`text/xml`, `text/html`) must select(`text/xml`, `UTF-8`)
        accept(`text/plain`, `text/xml`) must select(`text/xml`, `UTF-8`)
        accept(`text/plain`, `text/html`) must select(`text/html`, `UTF-8`)
      }

      """Accept: text/html, text/plain;q=0.8, application/*;q=.5, *;q= .2
         Accept-Charset: UTF-16""" ! test { accept ⇒
        accept(`text/plain`, `text/html`, `audio/ogg`) must select(`text/html`, `UTF-16`)
        accept(`text/plain`, `text/html` withCharset `UTF-8`, `audio/ogg`) must select(`text/plain`, `UTF-16`)
        accept(`audio/ogg`, `application/javascript`, `text/plain` withCharset `UTF-8`) must select(`application/javascript`, `UTF-16`)
        accept(`image/gif`, `application/javascript`) must select(`application/javascript`, `UTF-16`)
        accept(`image/gif`, `audio/ogg`) must select(`image/gif`, `UTF-16`)
      }
    }
  }

  def test[U](body: ((ContentType*) ⇒ Option[ContentType]) ⇒ U): String ⇒ U = { example ⇒
    val headers =
      if (example != "(without headers)") {
        example.split('\n').toList map { rawHeader ⇒
          val Array(name, value) = rawHeader.split(':')
          HttpParser.parseHeader(RawHeader(name.trim, value.trim)) match {
            case Right(header) ⇒ header
            case Left(err)     ⇒ throw new FailureException(Failure(err.formatPretty))
          }
        }
      } else Nil
    val request = HttpRequest(headers = headers)
    body(request.acceptableContentType)
  }

  def reject = beEqualTo(None)
  def select(mediaType: MediaType, charset: HttpCharset) = beEqualTo(Some(ContentType(mediaType, charset)))
}