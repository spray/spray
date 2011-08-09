/*
 * Copyright (C) 2011 Mathias Doenitz
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

package cc.spray.http

import org.specs2.mutable._
import HttpHeaders._

class HttpHeaderSpec extends Specification {

  "Header 'Accept'" should {
    import MediaTypes._
    import MediaRanges._
    "be parsed correctly from various examples" in {
      HttpHeader("Accept", "audio/mp4; q=0.2, audio/basic") mustEqual Accept(`audio/mp4`, `audio/basic`);
      HttpHeader("Accept", "text/plain; q=0.5, text/html,\r\n text/css; q=0.8") mustEqual
              Accept(`text/plain`, `text/html`, `text/css`)
      HttpHeader("Accept", "text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2") mustEqual
              Accept(`text/html`, `image/gif`, `image/jpeg`, `*/*`, `*/*`)
    }
  }

  "Header 'Accept-Charset'" should {
    import HttpCharsets._
    "be parsed correctly from various examples" in {
      HttpHeader("Accept-Charset", "iso-8859-5, UTF-16LE;q =0.8") mustEqual `Accept-Charset`(`ISO-8859-5`, `UTF-16LE`)
      HttpHeader("Accept-Charset", "*") mustEqual `Accept-Charset`(`*`)
      HttpHeader("Accept-Charset", "pipapo; q= 1.0, utf-8") mustEqual `Accept-Charset`(CustomHttpCharset("pipapo"), `UTF-8`)
    }
  }

  "Header 'Accept-Encoding'" should {
    import HttpEncodings._
    "be parsed correctly from various examples" in {
      HttpHeader("Accept-Encoding", "compress, gzip, fancy") mustEqual `Accept-Encoding`(compress, gzip, CustomHttpEncoding("fancy"))
      HttpHeader("Accept-Encoding", "gzip;q=1.0, identity; q=0.5, *;q=0") mustEqual `Accept-Encoding`(gzip, identity, `*`)
    }
  }

  "Header 'Accept-Language'" should {
    import LanguageRanges._
    "be parsed correctly from various examples" in {
      HttpHeader("Accept-Language", "da, en-gb ;q=0.8, en;q=0.7") mustEqual
              `Accept-Language`(Language("da"), Language("en", "gb"), Language("en"))
      HttpHeader("Accept-Language", "de-at-zz, *;q=0") mustEqual
              `Accept-Language`(Language("de", "at", "zz"), `*`)
    }
  }

  "Header 'Accept-Ranges'" should {
    import RangeUnits._
    "be parsed correctly from various examples" in {
      HttpHeader("Accept-Ranges", "bytes") mustEqual `Accept-Ranges`(bytes)
      HttpHeader("Accept-Ranges", "none") mustEqual `Accept-Ranges`(Nil)
      HttpHeader("Accept-Ranges", "bytes, fancy") mustEqual `Accept-Ranges`(bytes, CustomRangeUnit("fancy"))
    }
  }

  "Header 'Authorization'" should {
    "be parsed correctly from various examples" in {
      HttpHeader("Authorization", "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==") mustEqual
              Authorization(BasicHttpCredentials("Aladdin", "open sesame"))
      HttpHeader("Authorization", """Fancy yes="no", nonce="42"""") mustEqual
              Authorization(OtherHttpCredentials("Fancy", Map("yes"->"no", "nonce"->"42")))
      HttpHeader("Authorization", BasicHttpCredentials("Bob", "").value) mustEqual
              Authorization(BasicHttpCredentials("Bob", ""))
      HttpHeader("Authorization", OtherHttpCredentials("Digest", Map("name"->"Bob")).value) mustEqual
              Authorization(OtherHttpCredentials("Digest", Map("name"->"Bob")))
    }
  }

  "Header 'Cache-Control'" should {
    import CacheDirectives._
    "be parsed correctly from various examples" in {
      HttpHeader("Cache-Control", "no-cache, max-age=0") mustEqual `Cache-Control`(`no-cache`, `max-age`(0))
      HttpHeader("Cache-Control", "private=\"Some-Field\"") mustEqual `Cache-Control`(`private`(List("Some-Field")))
      HttpHeader("Cache-Control", "private, community=\"UCI\"") mustEqual
              `Cache-Control`(`private`(), CustomCacheDirective("community", Some("UCI")))
    }
  }

  "Header 'Connection'" should {
    import ConnectionTokens._
    "be parsed correctly from various examples" in {
      HttpHeader("Connection", "close") mustEqual Connection(close)
      HttpHeader("Connection", "pipapo, close") mustEqual Connection(CustomConnectionToken("pipapo"), close)
    }
  }

  "Header 'Content-Encoding'" should {
    import HttpEncodings._
    "be parsed correctly from various examples" in {
      HttpHeader("Content-Encoding", "gzip") mustEqual `Content-Encoding`(`gzip`)
      HttpHeader("Content-Encoding", "pipapo") mustEqual `Content-Encoding`(CustomHttpEncoding("pipapo"))
    }
  }

  "Header 'Content-Length'" should {
    "be parsed correctly from various examples" in {
      HttpHeader("Content-Length", "42") mustEqual `Content-Length`(42)
    }
  }

  "Header 'Content-Type'" should {
    import MediaTypes._
    import HttpCharsets._
    "be parsed correctly from various examples" in {
      HttpHeader("Content-Type", "application/pdf") mustEqual `Content-Type`(`application/pdf`)
      HttpHeader("Content-Type", "text/plain; charset=utf8") mustEqual `Content-Type`(ContentType(`text/plain`, `UTF-8`))
    }
  }

  "Header 'Cookie'" should {
    "be parsed correctly from various examples" in {
      HttpHeader("Cookie", "SID=31d4d96e407aad42") mustEqual `Cookie`(HttpCookie("SID", "31d4d96e407aad42"))
      HttpHeader("Cookie", "SID=31d4d96e407aad42; lang=\"en-US\"") mustEqual
              `Cookie`(HttpCookie("SID", "31d4d96e407aad42"), HttpCookie("lang", "en-US"))
    }
  }

  "Header 'Date'" should {
    "be parsed correctly from various examples" in {
      HttpHeader("Date", "Wed, 13 Jul 2011 08:12:31 GMT") mustEqual Date(DateTime(2011, 7, 13, 8, 12, 31))
      HttpHeader("Date", "Fri, 23 Mar 1804 12:11:10 GMT") mustEqual Date(DateTime(1804, 3, 23, 12, 11, 10))
    }
  }

  "Header 'Set-Cookie'" should {
    "be parsed correctly from various examples" in {
      HttpHeader("Set-Cookie", "SID=31d4d96e407aad42") mustEqual `Set-Cookie`(HttpCookie("SID", "31d4d96e407aad42"))
      HttpHeader("Set-Cookie", "SID=31d4d96e407aad42; Path=/; Domain=example.com") mustEqual
              `Set-Cookie`(HttpCookie("SID", "31d4d96e407aad42", path = Some("/"), domain = Some("example.com")))
      HttpHeader("Set-Cookie", "lang=en-US; Path=/; Expires=Wed, 09 Jun 2021 10:18:14 GMT; Path=/hello") mustEqual
              `Set-Cookie`(HttpCookie("lang", "en-US", expires = Some(DateTime(2021, 6, 9, 10, 18, 14)), path = Some("/hello")))
      HttpHeader("Set-Cookie", "name=123; Max-Age=12345; Secure") mustEqual
              `Set-Cookie`(HttpCookie("name", "123", maxAge = Some(12345), secure = true))
      HttpHeader("Set-Cookie", "name=123; fancyPants; HttpOnly") mustEqual
              `Set-Cookie`(HttpCookie("name", "123", httpOnly = true, extension = Some("fancyPants")))
    }
  }

  "Header 'WWW-Authenticate'" should {
    "be parsed correctly from various examples" in {
      HttpHeader("WWW-Authenticate", "Basic realm=\"WallyWorld\"") mustEqual
              `WWW-Authenticate`(HttpChallenge("Basic", "WallyWorld"))
      HttpHeader("WWW-Authenticate",
        """Digest
           realm="testrealm@host.com",
           qop="auth,auth-int",
           nonce=dcd98b7102dd2f0e8b11d0f600bfb0c093,
           opaque="5ccc069c403ebaf9f0171e9517f40e41"""".replace("\n", "\r\n")
      ) mustEqual `WWW-Authenticate`(HttpChallenge("Digest", "testrealm@host.com", Map(
          "qop" -> "auth,auth-int",
          "nonce" -> "dcd98b7102dd2f0e8b11d0f600bfb0c093",
          "opaque" -> "5ccc069c403ebaf9f0171e9517f40e41"
      )))
      HttpHeader("WWW-Authenticate", "Basic realm=WallyWorld,attr=value, Fancy realm=yeah") mustEqual
              `WWW-Authenticate`(
                HttpChallenge("Basic", "WallyWorld", Map("attr" -> "value")),
                HttpChallenge("Fancy", "yeah")
              )
    }
    "serialize properly to String" in {
      `WWW-Authenticate`(HttpChallenge("Fancy", "Secure Area", Map("nonce"->"42"))).value mustEqual
              """Fancy realm="Secure Area",nonce="42""""
    }
  }

  "Header 'X-Forwarded-For'" should {
    "be parsed correctly from various examples" in {
      HttpHeader("X-Forwarded-For", "1.2.3.4") mustEqual `X-Forwarded-For`("1.2.3.4")
      HttpHeader("X-Forwarded-For", "234.123.5.6 , 8.8.8.8") mustEqual `X-Forwarded-For`("234.123.5.6", "8.8.8.8")
    }
  }

  "Header 'CustomHeader'" should {
    "be parsed correctly from various examples" in {
      HttpHeader("X-Space-Ranger", "no, this rock!") mustEqual CustomHeader("X-Space-Ranger", "no, this rock!")
    }
  }
}