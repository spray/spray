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

package spray.can.parsing

import com.typesafe.config.{ ConfigFactory, Config }
import org.specs2.mutable.Specification
import akka.actor.ActorSystem
import akka.util.CompactByteString
import spray.util.Utils._
import spray.http.HttpHeaders._
import spray.http.HttpHeader

class HttpHeaderParserSpec extends Specification {
  val testConf: Config = ConfigFactory.parseString("""
    akka.event-handlers = ["akka.testkit.TestEventListener"]
    akka.loglevel = WARNING
    spray.can.parsing.max-header-name-length = 20
    spray.can.parsing.max-header-value-length = 21""")
  val system = ActorSystem(actorSystemNameFrom(getClass), testConf)

  "The HttpHeaderParser" should {
    "insert the 1st value" in new TestSetup(primed = false) {
      insert("Hello", 'Hello)
      parser.inspectRaw ===
        """nodes: 0/'H, 0/'e, 0/'l, 0/'l, 1/'o
          |nodeData: 0/-1/0
          |values: 'Hello""".stripMargin
      parser.inspect === "-H-e-l-l-o 'Hello\n"
    }

    "insert a new branch underneath a simple node" in new TestSetup(primed = false) {
      insert("Hello", 'Hello)
      insert("Hallo", 'Hallo)
      parser.inspectRaw ===
        """nodes: 0/'H, 2/'e, 0/'l, 0/'l, 1/'o, 0/'a, 0/'l, 0/'l, 3/'o
          |nodeData: 0/-1/0, 5/2/0, 0/-2/0
          |values: 'Hello, 'Hallo""".stripMargin
      parser.inspect ===
        """   ┌─a-l-l-o 'Hallo
          |-H-e-l-l-o 'Hello
          |""".stripMargin
    }

    "insert a new branch underneath the root" in new TestSetup(primed = false) {
      insert("Hello", 'Hello)
      insert("Hallo", 'Hallo)
      insert("Yeah", 'Yeah)
      parser.inspectRaw ===
        """nodes: 4/'H, 2/'e, 0/'l, 0/'l, 1/'o, 0/'a, 0/'l, 0/'l, 3/'o, 0/'Y, 0/'e, 0/'a, 5/'h
          |nodeData: 0/-1/0, 5/2/0, 0/-2/0, 0/1/9, 0/-3/0
          |values: 'Hello, 'Hallo, 'Yeah""".stripMargin
      parser.inspect ===
        """   ┌─a-l-l-o 'Hallo
          |-H-e-l-l-o 'Hello
          | └─Y-e-a-h 'Yeah
          |""".stripMargin
    }

    "insert a new branch underneath an existing branch node" in new TestSetup(primed = false) {
      insert("Hello", 'Hello)
      insert("Hallo", 'Hallo)
      insert("Yeah", 'Yeah)
      insert("Hoo", 'Hoo)
      parser.inspectRaw ===
        """nodes: 4/'H, 2/'e, 0/'l, 0/'l, 1/'o, 0/'a, 0/'l, 0/'l, 3/'o, 0/'Y, 0/'e, 0/'a, 5/'h, 0/'o, 6/'o
          |nodeData: 0/-1/0, 5/2/13, 0/-2/0, 0/1/9, 0/-3/0, 0/-4/0
          |values: 'Hello, 'Hallo, 'Yeah, 'Hoo""".stripMargin
      parser.inspect ===
        """   ┌─a-l-l-o 'Hallo
          |-H-e-l-l-o 'Hello
          | | └─o-o 'Hoo
          | └─Y-e-a-h 'Yeah
          |""".stripMargin
    }

    "support overriding of previously inserted values" in new TestSetup(primed = false) {
      insert("Hello", 'Hello)
      insert("Hallo", 'Hallo)
      insert("Yeah", 'Yeah)
      insert("Hoo", 'Hoo)
      insert("Hoo", 'Foo)
      parser.inspect ===
        """   ┌─a-l-l-o 'Hallo
          |-H-e-l-l-o 'Hello
          | | └─o-o 'Foo
          | └─Y-e-a-h 'Yeah
          |""".stripMargin
    }

    "prime an empty parser with all defined HeaderValueParsers" in new TestSetup() {
      parser.inspect ===
        """   ┌─\r-\n EmptyHeader
          |   |             ┌─c-h-a-r-s-e-t-: (Accept-Charset)
          |   |             | └─e-n-c-o-d-i-n-g-: (Accept-Encoding)
          | ┌─a-c-c-e-p-t---l-a-n-g-u-a-g-e-: (Accept-Language)
          | |   |         | └─r-a-n-g-e-s-: (Accept-Ranges)
          | |   |         |               ┌─\r-\n Accept: */*
          | |   |         └─:(Accept)- -*-/-*-\r-\n Accept: */*
          | |   └─u-t-h-o-r-i-z-a-t-i-o-n-: (Authorization)
          | | ┌─a-c-h-e---c-o-n-t-r-o-l-:(Cache-Control)- -m-a-x---a-g-e-=-0-\r-\n Cache-Control: max-age=0
          | | |     ┌─n-e-c-t-i-o-n-:(Connection)- -K-e-e-p---A-l-i-v-e-\r-\n Connection: Keep-Alive
          | | |     |                               | ┌─c-l-o-s-e-\r-\n Connection: close
          | | |     |                               └─k-e-e-p---a-l-i-v-e-\r-\n Connection: keep-alive
          | | |     |         ┌─d-i-s-p-o-s-i-t-i-o-n-: (Content-Disposition)
          | | | ┌─n-t-e-n-t---e-n-c-o-d-i-n-g-: (Content-Encoding)
          | | | |             | ┌─l-e-n-g-t-h-: (Content-Length)
          | | | |             └─t-y-p-e-: (Content-Type)
          |-c-o-o-k-i-e-: (Cookie)
          | |     ┌─d-a-t-e-: (Date)
          | |   ┌─h-o-s-t-: (Host)
          | | ┌─l-a-s-t---m-o-d-i-f-i-e-d-: (Last-Modified)
          | | | | └─o-c-a-t-i-o-n-: (Location)
          | | | └─r-e-m-o-t-e---a-d-d-r-e-s-s-: (Remote-Address)
          | └─s-e-r-v-e-r-: (Server)
          |   |   └─t---c-o-o-k-i-e-: (Set-Cookie)
          |   | ┌─t-r-a-n-s-f-e-r---e-n-c-o-d-i-n-g-: (Transfer-Encoding)
          |   └─u-s-e-r---a-g-e-n-t-: (User-Agent)
          |     | ┌─w-w-w---a-u-t-h-e-n-t-i-c-a-t-e-: (WWW-Authenticate)
          |     └─x---f-o-r-w-a-r-d-e-d---f-o-r-: (X-Forwarded-For)
          |""".stripMargin
      parser.inspectSizes === "300 nodes, 52 nodeData rows, 31 values"
    }

    "retrieve a cached header with an exact header name match" in new TestSetup() {
      parseAndCache("Connection: close\r\n")() === Connection("close")
    }

    "retrieve a cached header with a case-insensitive header-name match" in new TestSetup() {
      parseAndCache("Connection: close\r\n")("coNNection: close\r\n") === Connection("close")
    }

    "parse and cache a modelled header" in new TestSetup() {
      parseAndCache("Host: spray.io:123\r\nx")("HOST: spray.io:123\r\nx") === Host("spray.io", 123)
    }

    "parse and cache a raw header" in new TestSetup(primed = false) {
      insert("hello: bob", 'Hello)
      val (ixA, headerA) = parseLine("Fancy-Pants: foo\r\nx")
      val (ixB, headerB) = parseLine("Fancy-pants: foo\r\nx")
      parser.inspect ===
        """ ┌─f-a-n-c-y---p-a-n-t-s-:(Fancy-Pants)- -f-o-o-\r-\n *Fancy-Pants: foo
          |-h-e-l-l-o-:- -b-o-b 'Hello
          |""".stripMargin
      ixA === ixB
      headerA === RawHeader("Fancy-Pants", "foo")
      headerA must beTheSameAs(headerB)
    }

    "parse and cache a modelled header with line-folding" in new TestSetup() {
      parseAndCache("Connection: foo,\r\n bar\r\nx")("Connection: foo,\r\n bar\r\nx") === Connection("foo", "bar")
    }

    "parse and cache a header with a tab char in the value" in new TestSetup() {
      parseAndCache("Fancy: foo\tbar\r\nx")() === RawHeader("Fancy", "foo bar")
    }

    "produce an error message for lines with an illegal header name" in new TestSetup() {
      parseLine(" Connection: close\r\nx") must throwA[ParsingException]("Illegal character ' ' in header name")
      parseLine("Connection : close\r\nx") must throwA[ParsingException]("Illegal character ' ' in header name")
      parseLine("Connec/tion: close\r\nx") must throwA[ParsingException]("Illegal character '/' in header name")
    }

    "produce an error message for lines with a too-long header value" in new TestSetup() {
      parseLine("foo: 1234567890123456789012\r\nx") must
        throwA[ParsingException]("HTTP header value exceeds the configured limit of 21 characters")
    }
  }

  step(system.shutdown())

  abstract class TestSetup(primed: Boolean = true) extends org.specs2.specification.Scope {
    val parser = {
      val p = HttpHeaderParser(ParserSettings(system), info ⇒ system.log.warning(info.formatPretty))
      if (primed) HttpHeaderParser.prime(p) else p
    }
    def insert(line: String, value: AnyRef): Unit =
      if (parser.isEmpty) parser.insertRemainingCharsAsNewNodes(CompactByteString(line), value)()
      else parser.insert(CompactByteString(line), value)()

    def parseLine(line: String) = parser.parseHeaderLine(CompactByteString(line))() -> parser.resultHeader

    def parseAndCache(lineA: String)(lineB: String = lineA): HttpHeader = {
      val (ixA, headerA) = parseLine(lineA)
      val (ixB, headerB) = parseLine(lineB)
      ixA === ixB
      headerA must beTheSameAs(headerB)
      headerA
    }
  }
}
