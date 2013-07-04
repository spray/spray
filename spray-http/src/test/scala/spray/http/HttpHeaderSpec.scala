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

package spray.http

import org.specs2.Specification
import CacheDirectives._
import HttpHeaders._
import MediaTypes._
import MediaRanges._
import HttpCharsets._
import HttpEncodings._
import parser.HttpParser

class HttpHeaderSpec extends Specification {

  val EOL = System.getProperty("line.separator")
  val `application/vnd.spray` = MediaTypes.register(MediaType.custom("application/vnd.spray"))

  def is =

    "The HTTP header model must correctly parse and render the following examples" ^
      p ^
      "Accept: audio/midi; q=0.2, audio/basic" !
      example(Accept(`audio/midi`, `audio/basic`))_ ^
      "Accept: text/plain; q=0.5, text/html,\r\n text/css; q=0.8" !
      example(Accept(`text/plain`, `text/html`, `text/css`))_ ^
      "Accept: text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2" !
      example(Accept(`text/html`, `image/gif`, `image/jpeg`, `*/*`, `*/*`), fix(_).replace("*,", "*/*,"))_ ^
      "Accept: application/vnd.spray" !
      example(Accept(`application/vnd.spray`))_ ^
      "Accept: */*, text/*; foo=bar, custom/custom; bar=\"b>az\"" !
      example(Accept(`*/*`, MediaRange.custom("text", Map("foo" -> "bar")), MediaType.custom("custom", "custom", parameters = Map("bar" -> "b>az"))))_ ^
      p ^
      "Accept-Charset: utf8; q=0.5, *" !
      example(`Accept-Charset`(`UTF-8`, HttpCharsets.`*`), fix(_).replace("utf", "UTF-"))_ ^
      p ^
      "Accept-Encoding: compress, gzip, fancy" !
      example(`Accept-Encoding`(compress, gzip, HttpEncoding.custom("fancy")))_ ^
      "Accept-Encoding: gzip;q=1.0, identity; q=0.5, *;q=0" !
      example(`Accept-Encoding`(gzip, identity, HttpEncodings.`*`))_ ^
      p ^
      "Accept-Language: da, en-gb ;q=0.8, en;q=0.7" !
      example(`Accept-Language`(Language("da"), Language("en", "gb"), Language("en")))_ ^
      "Accept-Language: de-CH-1901, *;q=0" !
      example(`Accept-Language`(Language("de", "CH", "1901"), LanguageRanges.`*`))_ ^
      "Accept-Language: es-419, es" !
      example(`Accept-Language`(Language("es", "419"), Language("es")))_ ^
      p ^
      "Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==" !
      example(Authorization(BasicHttpCredentials("Aladdin", "open sesame")))_ ^
      """Authorization: Fancy yes="n:o", nonce=42""" !
      example(Authorization(GenericHttpCredentials("Fancy", Map("yes" -> "n:o", "nonce" -> "42"))), fix(_).replace(", ", ","))_ ^
      """Authorization: Fancy yes=no,nonce="4\\2"""" !
      example(Authorization(GenericHttpCredentials("Fancy", Map("yes" -> "no", "nonce" -> """4\2"""))))_ ^
      "Authorization: Basic Qm9iOg==" !
      example(Authorization(BasicHttpCredentials("Bob", "")))_ ^
      """Authorization: Digest name=Bob""" !
      example(Authorization(GenericHttpCredentials("Digest", Map("name" -> "Bob"))))_ ^
      """Authorization: Bearer mF_9.B5f-4.1JqM""" !
      example(Authorization(OAuth2BearerToken("mF_9.B5f-4.1JqM")))_ ^
      "Authorization: NoParamScheme" !
      example(Authorization(GenericHttpCredentials("NoParamScheme", Map.empty[String, String])))_ ^
      "Authorization: OAuth sf_v1a-stg;V5DrRS1KfA=" !
      example(Authorization(GenericHttpCredentials("OAuth", "sf_v1a-stg;V5DrRS1KfA=")))_ ^
      p ^
      "Cache-Control: no-cache, max-age=0" !
      example(`Cache-Control`(`no-cache`, `max-age`(0)))_ ^
      "Cache-Control: private=\"Some-Field\"" !
      example(`Cache-Control`(`private`("Some-Field")))_ ^
      "Cache-Control: private, community=\"<UCI>\"" !
      example(`Cache-Control`(`private`(), CacheDirective.custom("community", Some("<UCI>"))))_ ^
      p ^
      "Connection: close" ! example(Connection("close"))_ ^
      "Connection: pipapo, close" ! example(Connection("pipapo", "close"))_ ^
      p ^
      "Content-Disposition: form-data" ! example(`Content-Disposition`("form-data"))_ ^
      "Content-Disposition: attachment; name=field1; filename=\"file/txt\"" !
      example(`Content-Disposition`("attachment", Map("name" -> "field1", "filename" -> "file/txt")))_ ^
      p ^
      "Content-Encoding: gzip" ! example(`Content-Encoding`(gzip))_ ^
      "Content-Encoding: pipapo" ! example(`Content-Encoding`(HttpEncoding.custom("pipapo")))_ ^
      p ^
      "Content-Length: 42" ! example(`Content-Length`(42))_ ^
      p ^
      "Content-Type: application/pdf" !
      example(`Content-Type`(`application/pdf`))_ ^
      "Content-Type: text/plain; charset=utf8" !
      example(`Content-Type`(ContentType(`text/plain`, `UTF-8`)), fix(_).replace("utf", "UTF-"))_ ^
      "Content-Type: text/xml; version=3; charset=windows-1252" !
      example(`Content-Type`(ContentType(MediaType.custom("text", "xml", parameters = Map("version" -> "3")), HttpCharsets.getForKey("windows-1252"))))_ ^
      "Content-Type: text/plain; charset=fancy-pants" !
      errorExample(ErrorInfo("Illegal HTTP header 'Content-Type': Unsupported charset", "fancy-pants"))_ ^
      "Content-Type: multipart/mixed; boundary=ABC123" ! example(`Content-Type`(ContentType(new `multipart/mixed`("ABC123"))))_ ^
      "Content-Type: multipart/mixed; boundary=\"ABC/123\"" ! example(`Content-Type`(ContentType(new `multipart/mixed`("ABC/123"))))_ ^
      p ^
      "Cookie: SID=31d4d96e407aad42" ! example(`Cookie`(HttpCookie("SID", "31d4d96e407aad42")))_ ^
      "Cookie: SID=31d4d96e407aad42; lang=en>US" ! example(`Cookie`(HttpCookie("SID", "31d4d96e407aad42"), HttpCookie("lang", "en>US")))_ ^
      "Cookie: a=1;b=2" ! example(`Cookie`(HttpCookie("a", "1"), HttpCookie("b", "2")), fix(_).replace(";", "; "))_ ^
      "Cookie: a=1 ;b=2" ! example(`Cookie`(HttpCookie("a", "1"), HttpCookie("b", "2")), fix(_).replace(" ;", "; "))_ ^
      "Cookie: a=1; b=2" ! example(`Cookie`(HttpCookie("a", "1"), HttpCookie("b", "2")))_ ^
      p ^
      "Date: Wed, 13 Jul 2011 08:12:31 GMT" ! example(Date(DateTime(2011, 7, 13, 8, 12, 31)))_ ^
      "Date: Fri, 23 Mar 1804 12:11:10 UTC" ! example(Date(DateTime(1804, 3, 23, 12, 11, 10)), _.replace("UTC", "GMT"))_ ^
      p ^
      "Expect: 100-continue" ! example(Expect("100-continue"))_ ^
      p ^
      "Host: www.spray.io:8080" ! example(Host("www.spray.io", 8080))_ ^
      "Host: spray.io" ! example(Host("spray.io"))_ ^
      "Host: [2001:db8::1]:8080" ! example(Host("[2001:db8::1]", 8080))_ ^
      "Host: [2001:db8::1]" ! example(Host("[2001:db8::1]"))_ ^
      "Host: [::FFFF:129.144.52.38]" ! example(Host("[::FFFF:129.144.52.38]"))_ ^
      p ^
      "Last-Modified: Wed, 13 Jul 2011 08:12:31 GMT" ! example(`Last-Modified`(DateTime(2011, 7, 13, 8, 12, 31)))_ ^
      p ^
      "Location: https://spray.io/secure" ! example(Location(Uri("https://spray.io/secure")))_ ^
      "Location: https://spray.io/{sec}" ! example(Location(Uri("https://spray.io/{sec}")), fix(_).replace("{", "%7B").replace("}", "%7D"))_ ^
      "Location: https://spray.io/ sec" ! errorExample(ErrorInfo("Illegal HTTP header 'Location': Illegal absolute " +
        "URI, unexpected character ' ' at position 17", "\nhttps://spray.io/ sec\n                 ^\n"))_ ^
      p ^
      "Remote-Address: 111.22.3.4" ! example(`Remote-Address`("111.22.3.4"))_ ^
      p ^
      "Server: as fghf.fdf/xx" ! example(`Server`(Seq(ProductVersion("as"), ProductVersion("fghf.fdf", "xx"))))_ ^
      p ^
      "Transfer-Encoding: chunked" ! example(`Transfer-Encoding`("chunked"))_ ^
      p ^
      "Set-Cookie: SID=\"31d4d96e407aad42\"" !
      example(`Set-Cookie`(HttpCookie("SID", "31d4d96e407aad42")), fix(_).replace("=\"", "=").dropRight(1))_ ^
      "Set-Cookie: SID=31d4d96e407aad42; Domain=example.com; Path=/" !
      example(`Set-Cookie`(HttpCookie("SID", "31d4d96e407aad42", path = Some("/"), domain = Some("example.com"))))_ ^
      "Set-Cookie: lang=en-US; Expires=Wed, 09 Jun 2021 10:18:14 GMT; Path=/hello" !
      example(`Set-Cookie`(HttpCookie("lang", "en-US", expires = Some(DateTime(2021, 6, 9, 10, 18, 14)), path = Some("/hello"))))_ ^
      "Set-Cookie: name=123; Max-Age=12345; Secure" !
      example(`Set-Cookie`(HttpCookie("name", "123", maxAge = Some(12345), secure = true)))_ ^
      "Set-Cookie: name=123; HttpOnly; fancyPants" !
      example(`Set-Cookie`(HttpCookie("name", "123", httpOnly = true, extension = Some("fancyPants"))))_ ^
      "Set-Cookie: foo=bar; domain=example.com; Path=/this is a path with blanks; extension with blanks" !
      example(`Set-Cookie`(HttpCookie("foo", "bar", domain = Some("example.com"), path = Some("/this is a path with blanks"),
        extension = Some("extension with blanks"))), fix(_).replace("domain", "Domain"))_ ^
      p ^
      "User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_8_3) AppleWebKit/537.31" !
      example(`User-Agent`(ProductVersion("Mozilla", "5.0", "Macintosh; Intel Mac OS X 10_8_3"), ProductVersion("AppleWebKit", "537.31")))_ ^
      "User-Agent: foo(bar)(baz)" !
      example(`User-Agent`(ProductVersion("foo", "", "bar"), ProductVersion(comment = "baz")), _.replace("o(", "o (").replace(")(", ") ("))_ ^
      p ^
      "WWW-Authenticate: Basic realm=WallyWorld" ! example(`WWW-Authenticate`(HttpChallenge("Basic", "WallyWorld")))_ ^
      "WWW-Authenticate: Basic realm=\"foo<bar\"" ! example(`WWW-Authenticate`(HttpChallenge("Basic", "foo<bar")))_ ^
      """WWW-Authenticate: Digest
                         realm="testrealm@host.com",
                         qop="auth,auth-int",
                         nonce=dcd98b7102dd2f0e8b11d0f600bfb0c093,
                         opaque=5ccc069c403ebaf9f0171e9517f40e41""".replace(EOL, "\r\n") !
      example(`WWW-Authenticate`(HttpChallenge("Digest", "testrealm@host.com", Map("qop" -> "auth,auth-int", "nonce" -> "dcd98b7102dd2f0e8b11d0f600bfb0c093", "opaque" -> "5ccc069c403ebaf9f0171e9517f40e41"))), fix(_).replace(", ", ","))_ ^
      "WWW-Authenticate: Basic realm=WallyWorld,attr=\"val>ue\", Fancy realm=yeah" !
      example(`WWW-Authenticate`(HttpChallenge("Basic", "WallyWorld", Map("attr" -> "val>ue")), HttpChallenge("Fancy", "yeah")))_ ^
      """WWW-Authenticate: Fancy realm="Secure Area",nonce=42""" !
      example(`WWW-Authenticate`(HttpChallenge("Fancy", "Secure Area", Map("nonce" -> "42"))))_ ^
      p ^
      "X-Forwarded-For: 1.2.3.4" ! example(`X-Forwarded-For`("1.2.3.4"))_ ^
      "X-Forwarded-For: 234.123.5.6, 8.8.8.8" ! example(`X-Forwarded-For`("234.123.5.6", "8.8.8.8"))_ ^
      "X-Forwarded-For: 1.2.3.4, unknown" ! example(`X-Forwarded-For`(Seq(Some(HttpIp("1.2.3.4")), None)))_ ^
      p ^
      "X-Space-Ranger: no, this rock!" ! example(RawHeader("X-Space-Ranger", "no, this rock!"))_

  def example(expected: HttpHeader, fix: String ⇒ String = fix)(line: String) = {
    val Array(name, value) = line.split(": ", 2)
    HttpParser.parseHeader(RawHeader(name, value)) === Right(expected) and expected.toString === fix(line)
  }

  def fix(line: String) = line.replaceAll("""\s*;\s*q=\d?(\.\d)?""", "").replaceAll("""\s\s+""", " ")

  def errorExample(expectedError: ErrorInfo)(line: String) = {
    val Array(name, value) = line.split(": ", 2)
    HttpParser.parseHeader(RawHeader(name, value)) === Left(expectedError)
  }

}
