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

import org.specs2.mutable.Specification
import Uri._

class UriSpec extends Specification {

  "Uri.Host instances" should {

    "parse correctly from IPv4 literals" in {
      Host("192.0.2.16") === IPv4Host("192.0.2.16")
      Host("255.0.0.0") === IPv4Host("255.0.0.0")
      Host("0.0.0.0") === IPv4Host("0.0.0.0")
      Host("1.0.0.0") === IPv4Host("1.0.0.0")
      Host("2.0.0.0") === IPv4Host("2.0.0.0")
      Host("3.0.0.0") === IPv4Host("3.0.0.0")
      Host("30.0.0.0") === IPv4Host("30.0.0.0")
    }

    "parse correctly from IPv6 literals (RFC2732)" in {
      // various
      Host("[FEDC:BA98:7654:3210:FEDC:BA98:7654:3210]") === IPv6Host("FEDC:BA98:7654:3210:FEDC:BA98:7654:3210")
      Host("[1080:0:0:0:8:800:200C:417A]") === IPv6Host("1080:0:0:0:8:800:200C:417A")
      Host("[3ffe:2a00:100:7031::1]") === IPv6Host("3ffe:2a00:100:7031::1")
      Host("[1080::8:800:200C:417A]") === IPv6Host("1080::8:800:200C:417A")
      Host("[::192.9.5.5]") === IPv6Host("::192.9.5.5")
      Host("[::FFFF:129.144.52.38]") === IPv6Host("::FFFF:129.144.52.38")
      Host("[2010:836B:4179::836B:4179]") === IPv6Host("2010:836B:4179::836B:4179")

      // Quad length
      Host("[abcd::]") === IPv6Host("abcd::")
      Host("[abcd::1]") === IPv6Host("abcd::1")
      Host("[abcd::12]") === IPv6Host("abcd::12")
      Host("[abcd::123]") === IPv6Host("abcd::123")
      Host("[abcd::1234]") === IPv6Host("abcd::1234")

      // Full length
      Host("[2001:0db8:0100:f101:0210:a4ff:fee3:9566]") === IPv6Host("2001:0db8:0100:f101:0210:a4ff:fee3:9566") // lower hex
      Host("[2001:0DB8:0100:F101:0210:A4FF:FEE3:9566]") === IPv6Host("2001:0DB8:0100:F101:0210:A4FF:FEE3:9566") // Upper hex
      Host("[2001:db8:100:f101:210:a4ff:fee3:9566]") === IPv6Host("2001:db8:100:f101:210:a4ff:fee3:9566")
      Host("[2001:0db8:100:f101:0:0:0:1]") === IPv6Host("2001:0db8:100:f101:0:0:0:1")
      Host("[1:2:3:4:5:6:255.255.255.255]") === IPv6Host("1:2:3:4:5:6:255.255.255.255")

      // Legal IPv4
      Host("[::1.2.3.4]") === IPv6Host("::1.2.3.4")
      Host("[3:4::5:1.2.3.4]") === IPv6Host("3:4::5:1.2.3.4")
      Host("[::ffff:1.2.3.4]") === IPv6Host("::ffff:1.2.3.4")
      Host("[::0.0.0.0]") === IPv6Host("::0.0.0.0") // Min IPv4
      Host("[::255.255.255.255]") === IPv6Host("::255.255.255.255") // Max IPv4

      // Zipper position
      Host("[::1:2:3:4:5:6:7]") === IPv6Host("::1:2:3:4:5:6:7")
      Host("[1::1:2:3:4:5:6]") === IPv6Host("1::1:2:3:4:5:6")
      Host("[1:2::1:2:3:4:5]") === IPv6Host("1:2::1:2:3:4:5")
      Host("[1:2:3::1:2:3:4]") === IPv6Host("1:2:3::1:2:3:4")
      Host("[1:2:3:4::1:2:3]") === IPv6Host("1:2:3:4::1:2:3")
      Host("[1:2:3:4:5::1:2]") === IPv6Host("1:2:3:4:5::1:2")
      Host("[1:2:3:4:5:6::1]") === IPv6Host("1:2:3:4:5:6::1")
      Host("[1:2:3:4:5:6:7::]") === IPv6Host("1:2:3:4:5:6:7::")

      // Zipper length
      Host("[1:1:1::1:1:1:1]") === IPv6Host("1:1:1::1:1:1:1")
      Host("[1:1:1::1:1:1]") === IPv6Host("1:1:1::1:1:1")
      Host("[1:1:1::1:1]") === IPv6Host("1:1:1::1:1")
      Host("[1:1::1:1]") === IPv6Host("1:1::1:1")
      Host("[1:1::1]") === IPv6Host("1:1::1")
      Host("[1::1]") === IPv6Host("1::1")
      Host("[::1]") === IPv6Host("::1") // == localhost
      Host("[::]") === IPv6Host("::") // == all addresses

      // A few more variations
      Host("[21ff:abcd::1]") === IPv6Host("21ff:abcd::1")
      Host("[2001:db8:100:f101::1]") === IPv6Host("2001:db8:100:f101::1")
      Host("[a:b:c::12:1]") === IPv6Host("a:b:c::12:1")
      Host("[a:b::0:1:2:3]") === IPv6Host("a:b::0:1:2:3")
    }

    "parse correctly from NamedHost literals" in {
      Host("www.spray.io") === NamedHost("www.spray.io")
      Host("localhost") === NamedHost("localhost")
      Host("%2FH%C3%A4ll%C3%B6%5C") === NamedHost("""/hällö\""")
    }

    "not accept illegal IPv4 literals" in {
      Host("01.0.0.0") must beAnInstanceOf[NamedHost]
      Host("001.0.0.0") must beAnInstanceOf[NamedHost]
      Host("00.0.0.0") must beAnInstanceOf[NamedHost]
      Host("000.0.0.0") must beAnInstanceOf[NamedHost]
      Host("256.0.0.0") must beAnInstanceOf[NamedHost]
      Host("300.0.0.0") must beAnInstanceOf[NamedHost]
      Host("1111.0.0.0") must beAnInstanceOf[NamedHost]
      Host("-1.0.0.0") must beAnInstanceOf[NamedHost]
      Host("0.0.0") must beAnInstanceOf[NamedHost]
      Host("0.0.0.") must beAnInstanceOf[NamedHost]
      Host("0.0.0.0.") must beAnInstanceOf[NamedHost]
      Host("0.0.0.0.0") must beAnInstanceOf[NamedHost]
      Host("0.0..0") must beAnInstanceOf[NamedHost]
      Host(".0.0.0") must beAnInstanceOf[NamedHost]
    }

    "not accept illegal IPv6 literals" in {
      // 5 char quad
      Host("[::12345]") must throwA {
        new IllegalUriException("Illegal URI host, unexpected character '5' at position 7",
          "\n[::12345]\n" +
            "       ^\n")
      }

      // Two zippers
      Host("[abcd::abcd::abcd]") must throwA[IllegalUriException]

      // Triple-colon zipper
      Host("[:::1234]") must throwA[IllegalUriException]
      Host("[1234:::1234:1234]") must throwA[IllegalUriException]
      Host("[1234:1234:::1234]") must throwA[IllegalUriException]
      Host("[1234:::]") must throwA[IllegalUriException]

      // No quads, just IPv4
      Host("[1.2.3.4]") must throwA[IllegalUriException]
      Host("[0001.0002.0003.0004]") must throwA[IllegalUriException]

      // Five quads
      Host("[0000:0000:0000:0000:0000:1.2.3.4]") must throwA[IllegalUriException]

      // Seven quads
      Host("[0:0:0:0:0:0:0]") must throwA[IllegalUriException]
      Host("[0:0:0:0:0:0:0:]") must throwA[IllegalUriException]
      Host("[0:0:0:0:0:0:0:1.2.3.4]") must throwA[IllegalUriException]

      // Nine quads
      Host("[0:0:0:0:0:0:0:0:0]") must throwA[IllegalUriException]

      // Invalid IPv4 part
      Host("[::ffff:001.02.03.004]") must throwA[IllegalUriException] // Leading zeros
      Host("[::ffff:1.2.3.1111]") must throwA[IllegalUriException] // Four char octet
      Host("[::ffff:1.2.3.256]") must throwA[IllegalUriException] // > 255
      Host("[::ffff:311.2.3.4]") must throwA[IllegalUriException] // > 155
      Host("[::ffff:1.2.3:4]") must throwA[IllegalUriException] // Not a dot
      Host("[::ffff:1.2.3]") must throwA[IllegalUriException] // Missing octet
      Host("[::ffff:1.2.3.]") must throwA[IllegalUriException] // Missing octet
      Host("[::ffff:1.2.3a.4]") must throwA[IllegalUriException] // Hex in octet
      Host("[::ffff:1.2.3.4:123]") must throwA[IllegalUriException] // Crap input

      // Nonhex
      Host("[g:0:0:0:0:0:0]") must throwA[IllegalUriException]
    }
  }

  "Uri.Path instances" should {
    import Path.Empty
    "be parsed and rendered correctly" in {
      Path("") === Empty
      Path("/") === Path./
      Path("a") === "a" :: Empty
      Path("//") === Path./ / ""
      Path("a/") === "a" :: Path./
      Path("/a") === Path / "a"
      Path("/abc/de/f") === Path / "abc" / "de" / "f"
      Path("abc/de/f/") === "abc" :: '/' :: "de" :: '/' :: "f" :: Path./
      Path("abc///de") === "abc" :: '/' :: '/' :: '/' :: "de" :: Empty
      Path("/abc%2F") === Path / "abc/"
      Path("H%C3%A4ll%C3%B6") === """Hällö""" :: Empty
      Path("/%2F%5C") === Path / """/\"""
      Path("/:foo:/") === Path / ":foo:" / ""
      Path("%2520").head === "%20"
    }
    "support the `startsWith` predicate" in {
      Empty startsWith Empty must beTrue
      Path./ startsWith Empty must beTrue
      Path("abc") startsWith Empty must beTrue
      Empty startsWith Path./ must beFalse
      Empty startsWith Path("abc") must beFalse
      Path./ startsWith Path./ must beTrue
      Path./ startsWith Path("abc") must beFalse
      Path("/abc") startsWith Path./ must beTrue
      Path("abc") startsWith Path./ must beFalse
      Path("abc") startsWith Path("ab") must beTrue
      Path("abc") startsWith Path("abc") must beTrue
      Path("/abc") startsWith Path("/a") must beTrue
      Path("/abc") startsWith Path("/abc") must beTrue
      Path("/ab") startsWith Path("/abc") must beFalse
      Path("/abc") startsWith Path("/abd") must beFalse
      Path("/abc/def") startsWith Path("/ab") must beTrue
      Path("/abc/def") startsWith Path("/abc/") must beTrue
      Path("/abc/def") startsWith Path("/abc/d") must beTrue
      Path("/abc/def") startsWith Path("/abc/def") must beTrue
      Path("/abc/def") startsWith Path("/abc/def/") must beFalse
    }
    "support the `dropChars` modifier" in {
      Path./.dropChars(0) === Path./
      Path./.dropChars(1) === Empty
      Path("/abc/def/").dropChars(0) === Path("/abc/def/")
      Path("/abc/def/").dropChars(1) === Path("abc/def/")
      Path("/abc/def/").dropChars(2) === Path("bc/def/")
      Path("/abc/def/").dropChars(3) === Path("c/def/")
      Path("/abc/def/").dropChars(4) === Path("/def/")
      Path("/abc/def/").dropChars(5) === Path("def/")
      Path("/abc/def/").dropChars(6) === Path("ef/")
      Path("/abc/def/").dropChars(7) === Path("f/")
      Path("/abc/def/").dropChars(8) === Path("/")
      Path("/abc/def/").dropChars(9) === Empty
    }
  }

  "Uri.Query instances" should {
    def parser(mode: Uri.ParsingMode): String ⇒ Query = Query(_, mode)
    "be parsed and rendered correctly in strict mode" in {
      val test = parser(Uri.ParsingMode.Strict)
      test("") === ("", "") +: Query.Empty
      test("a") === ("a", "") +: Query.Empty
      test("a=") === ("a", "") +: Query.Empty
      test("=a") === ("", "a") +: Query.Empty
      test("a&") === ("a", "") +: ("", "") +: Query.Empty
      test("a^=b") must throwAn[IllegalUriException]
    }
    "be parsed and rendered correctly in relaxed mode" in {
      val test = parser(Uri.ParsingMode.Relaxed)
      test("") === ("", "") +: Query.Empty
      test("a") === ("a", "") +: Query.Empty
      test("a=") === ("a", "") +: Query.Empty
      test("=a") === ("", "a") +: Query.Empty
      test("a&") === ("a", "") +: ("", "") +: Query.Empty
      test("a^=b") === ("a^", "b") +: Query.Empty
    }
    "be parsed and rendered correctly in relaxed-with-raw-query mode" in {
      val test = parser(Uri.ParsingMode.RelaxedWithRawQuery)
      test("a^=b&c").toString === "a^=b&c"
      test("a%2Fb") === Uri.Query.Raw("a%2Fb")
    }
    "properly support the retrieval interface" in {
      val query = Query("a=1&b=2&c=3&b=4&b")
      query.get("a") === Some("1")
      query.get("d") === None
      query.getOrElse("a", "x") === "1"
      query.getOrElse("d", "x") === "x"
      query.getAll("b") === List("", "4", "2")
      query.getAll("d") === Nil
      query.toMap === Map("a" -> "1", "b" -> "", "c" -> "3")
      query.toMultiMap === Map("a" -> List("1"), "b" -> List("", "4", "2"), "c" -> List("3"))
      query.toList === List("a" -> "1", "b" -> "2", "c" -> "3", "b" -> "4", "b" -> "")
      query.toSeq === Seq("a" -> "1", "b" -> "2", "c" -> "3", "b" -> "4", "b" -> "")
    }
    "support conversion from list of name/value pairs" in {
      import Query._
      val pairs = List("key1" -> "value1", "key2" -> "value2", "key3" -> "value3")
      Query(pairs: _*).toList.diff(pairs) === Nil
      Query() === Empty
      Query("k" -> "v") === ("k" -> "v") +: Empty
    }
  }

  "URIs" should {

    // http://tools.ietf.org/html/rfc3986#section-1.1.2
    "be correctly parsed from and rendered to simple test examples" in {
      Uri("ftp://ftp.is.co.za/rfc/rfc1808.txt") ===
        Uri.from(scheme = "ftp", host = "ftp.is.co.za", path = "/rfc/rfc1808.txt")

      Uri("http://www.ietf.org/rfc/rfc2396.txt") ===
        Uri.from(scheme = "http", host = "www.ietf.org", path = "/rfc/rfc2396.txt")

      Uri("ldap://[2001:db8::7]/c=GB?objectClass?one") ===
        Uri.from(scheme = "ldap", host = "[2001:db8::7]", path = "/c=GB", query = Query("objectClass?one"))

      Uri("mailto:John.Doe@example.com") ===
        Uri.from(scheme = "mailto", path = "John.Doe@example.com")

      Uri("news:comp.infosystems.www.servers.unix") ===
        Uri.from(scheme = "news", path = "comp.infosystems.www.servers.unix")

      Uri("tel:+1-816-555-1212") ===
        Uri.from(scheme = "tel", path = "+1-816-555-1212")

      Uri("telnet://192.0.2.16:80/") ===
        Uri.from(scheme = "telnet", host = "192.0.2.16", port = 80, path = "/")

      Uri("urn:oasis:names:specification:docbook:dtd:xml:4.1.2") ===
        Uri.from(scheme = "urn", path = "oasis:names:specification:docbook:dtd:xml:4.1.2")

      // more examples
      Uri("http://") === Uri(scheme = "http", authority = Authority(host = NamedHost("")))
      Uri("http:?") === Uri.from(scheme = "http", query = Query(""))
      Uri("?a+b=c%2Bd") === Uri.from(query = ("a b", "c+d") +: Query.Empty)

      // illegal paths
      Uri("foo/another@url/[]and{}") === Uri.from(path = "foo/another@url/%5B%5Dand%7B%7D")
      Uri("foo/another@url/[]and{}", mode = Uri.ParsingMode.Strict) must throwAn[IllegalUriException]

      // handle query parameters with more than percent-encoded character
      Uri("?%7Ba%7D=$%7B%7D", UTF8, Uri.ParsingMode.Strict) === Uri(query = Query.Cons("{a}", "${}", Query.Empty))

      // don't double decode
      Uri("%2520").path.head === "%20"
      Uri("/%2F%5C").path === Path / """/\"""
    }

    "properly complete a normalization cycle" in {

      // http://tools.ietf.org/html/rfc3986#section-6.2.2
      normalize("eXAMPLE://a/./b/../b/%63/%7bfoo%7d") === "example://a/b/c/%7Bfoo%7D"

      // more examples
      normalize("") === ""
      normalize("/") === "/"
      normalize("../../") === "../../"
      normalize("aBc") === "aBc"

      normalize("Http://Localhost") === "http://localhost"
      normalize("hTtP://localHost") === "http://localhost"
      normalize("https://:443") === "https://"
      normalize("ftp://example.com:21") === "ftp://example.com"
      normalize("example.com:21") === "example.com:21"
      normalize("ftp://example.com:22") === "ftp://example.com:22"

      normalize("//user:pass@[::1]:80/segment/index.html?query#frag") === "//user:pass@[::1]:80/segment/index.html?query#frag"
      normalize("http://[::1]:80/segment/index.html?query#frag") === "http://[::1]/segment/index.html?query#frag"
      normalize("http://user:pass@[::1]/segment/index.html?query#frag") === "http://user:pass@[::1]/segment/index.html?query#frag"
      normalize("http://user:pass@[::1]:80?query#frag") === "http://user:pass@[::1]?query#frag"
      normalize("http://user:pass@[::1]/segment/index.html#frag") === "http://user:pass@[::1]/segment/index.html#frag"
      normalize("http://user:pass@[::1]:81/segment/index.html?query") === "http://user:pass@[::1]:81/segment/index.html?query"
      normalize("ftp://host:21/gnu/") === "ftp://host/gnu/"
      normalize("one/two/three") === "one/two/three"
      normalize("/one/two/three") === "/one/two/three"
      normalize("//user:pass@localhost/one/two/three") === "//user:pass@localhost/one/two/three"
      normalize("http://www.example.com/") === "http://www.example.com/"
      normalize("http://sourceforge.net/projects/uriparser/") === "http://sourceforge.net/projects/uriparser/"
      normalize("http://sourceforge.net/project/platformdownload.php?group_id=182840") === "http://sourceforge.net/project/platformdownload.php?group_id=182840"
      normalize("mailto:test@example.com") === "mailto:test@example.com"
      normalize("file:///bin/bash") === "file:///bin/bash"
      normalize("http://www.example.com/name%20with%20spaces/") === "http://www.example.com/name%20with%20spaces/"
      normalize("http://examp%4Ce.com/") === "http://example.com/"
      normalize("http://example.com/a/b/%2E%2E/") === "http://example.com/a/"
      normalize("http://user:pass@SOMEHOST.COM:123") === "http://user:pass@somehost.com:123"
      normalize("HTTP://a:b@HOST:123/./1/2/../%41?abc#def") === "http://a:b@host:123/1/A?abc#def"

      // acceptance and normalization of unescaped ascii characters such as {} and []:
      normalize("eXAMPLE://a/./b/../b/%63/{foo}/[bar]") === "example://a/b/c/%7Bfoo%7D/%5Bbar%5D"
      normalize("eXAMPLE://a/./b/../b/%63/{foo}/[bar]", mode = Uri.ParsingMode.Strict) must throwAn[IllegalUriException]

      // queries
      normalize("?") === "?"
      normalize("?key") === "?key"
      normalize("?key=") === "?key" // our query model cannot discriminate between these two inputs
      normalize("?key=&a=b") === "?key&a=b" // our query model cannot discriminate between these two inputs
      normalize("?key={}&a=[]") === "?key=%7B%7D&a=%5B%5D"
      normalize("?key={}&a=[]", mode = Uri.ParsingMode.Strict) must throwAn[IllegalUriException]
      normalize("?=value") === "?=value"
      normalize("?key=value") === "?key=value"
      normalize("?a+b") === "?a+b"
      normalize("?=a+b") === "?=a+b"
      normalize("?a+b=c+d") === "?a+b=c+d"
      normalize("??") === "??"
      normalize("?a=1&b=2") === "?a=1&b=2"
      normalize("?a+b=c%2Bd") === "?a+b=c%2Bd"
      normalize("?a&a") === "?a&a"
      normalize("?&#") === "?&#"
      normalize("?#") === "?#"
      normalize("#") === "#"
      normalize("#{}[]") === "#%7B%7D%5B%5D"
      normalize("#{}[]", mode = Uri.ParsingMode.Strict) must throwAn[IllegalUriException]
    }

    "support tunneling a URI through a query param" in {
      val uri = Uri("http://aHost/aPath?aParam=aValue#aFragment")
      val q = Query("uri" -> uri.toString)
      val uri2 = Uri(path = Path./, query = q, fragment = Some("aFragment")).toString
      uri2 === "/?uri=http://ahost/aPath?aParam%3DaValue%23aFragment#aFragment"
      Uri(uri2).query === q
      Uri(q.getOrElse("uri", "<nope>")) === uri
    }

    "produce proper error messages for illegal URIs" in {
      // illegal scheme
      Uri("foö:/a") must throwA {
        new IllegalUriException("Illegal URI reference, unexpected character 'ö' at position 2",
          "\nfoö:/a\n" +
            "  ^\n")
      }

      // illegal userinfo
      Uri("http://user:ö@host") must throwA {
        new IllegalUriException("Illegal URI reference, unexpected character 'ö' at position 12",
          "\nhttp://user:ö@host\n" +
            "            ^\n")
      }

      // illegal percent-encoding
      Uri("http://use%2G@host") must throwA {
        new IllegalUriException("Illegal URI reference, unexpected character 'G' at position 12",
          "\nhttp://use%2G@host\n" +
            "            ^\n")
      }

      // illegal path
      Uri("http://www.example.com/name with spaces/") must throwA {
        new IllegalUriException("Illegal URI reference, unexpected character ' ' at position 27",
          "\nhttp://www.example.com/name with spaces/\n" +
            "                           ^\n")
      }

      // illegal path with control character
      Uri("http:///with\newline") must throwA {
        new IllegalUriException("Illegal URI reference, unexpected character '\\u000a' at position 12",
          "\nhttp:///with?ewline\n" +
            "            ^\n")
      }

      // illegal query
      Uri("?a=b=c") must throwA {
        new IllegalUriException("Illegal URI reference, unexpected character '=' at position 4",
          "\n?a=b=c\n" +
            "    ^\n")
      }
    }

    // http://tools.ietf.org/html/rfc3986#section-5.4
    "pass the RFC 3986 reference resolution examples" in {
      val base = parseAbsolute("http://a/b/c/d;p?q")
      def resolve(uri: String) = parseAndResolve(uri, base).toString

      "normal examples" in {
        resolve("g:h") === "g:h"
        resolve("g") === "http://a/b/c/g"
        resolve("./g") === "http://a/b/c/g"
        resolve("g/") === "http://a/b/c/g/"
        resolve("/g") === "http://a/g"
        resolve("//g") === "http://g"
        resolve("?y") === "http://a/b/c/d;p?y"
        resolve("g?y") === "http://a/b/c/g?y"
        resolve("#s") === "http://a/b/c/d;p?q#s"
        resolve("g#s") === "http://a/b/c/g#s"
        resolve("g?y#s") === "http://a/b/c/g?y#s"
        resolve(";x") === "http://a/b/c/;x"
        resolve("g;x") === "http://a/b/c/g;x"
        resolve("g;x?y#s") === "http://a/b/c/g;x?y#s"
        resolve("") === "http://a/b/c/d;p?q"
        resolve(".") === "http://a/b/c/"
        resolve("./") === "http://a/b/c/"
        resolve("..") === "http://a/b/"
        resolve("../") === "http://a/b/"
        resolve("../g") === "http://a/b/g"
        resolve("../..") === "http://a/"
        resolve("../../") === "http://a/"
        resolve("../../g") === "http://a/g"
      }

      "abnormal examples" in {
        resolve("../../../g") === "http://a/g"
        resolve("../../../../g") === "http://a/g"

        resolve("/./g") === "http://a/g"
        resolve("/../g") === "http://a/g"
        resolve("g.") === "http://a/b/c/g."
        resolve(".g") === "http://a/b/c/.g"
        resolve("g..") === "http://a/b/c/g.."
        resolve("..g") === "http://a/b/c/..g"

        resolve("./../g") === "http://a/b/g"
        resolve("./g/.") === "http://a/b/c/g/"
        resolve("g/./h") === "http://a/b/c/g/h"
        resolve("g/../h") === "http://a/b/c/h"
        resolve("g;x=1/./y") === "http://a/b/c/g;x=1/y"
        resolve("g;x=1/../y") === "http://a/b/c/y"

        resolve("g?y/./x") === "http://a/b/c/g?y/./x"
        resolve("g?y/../x") === "http://a/b/c/g?y/../x"
        resolve("g#s/./x") === "http://a/b/c/g#s/./x"
        resolve("g#s/../x") === "http://a/b/c/g#s/../x"

        resolve("http:g") === "http:g"
      }
    }

    "be properly copyable" in {
      val uri = Uri("http://host:80/path?query#fragment")
      uri.copy() === uri
    }
  }
}