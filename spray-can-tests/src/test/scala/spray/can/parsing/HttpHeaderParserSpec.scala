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

import java.lang.{ StringBuilder ⇒ JStringBuilder }
import com.typesafe.config.{ ConfigFactory, Config }
import org.specs2.mutable.Specification
import akka.actor.ActorSystem
import akka.util.ByteString
import spray.util.Utils._
import spray.http.HttpHeaders._
import spray.http.HttpHeader
import scala.util.Random
import scala.annotation.tailrec

class HttpHeaderParserSpec extends Specification {
  val testConf: Config = ConfigFactory.parseString("""
    akka.event-handlers = ["akka.testkit.TestEventListener"]
    akka.loglevel = WARNING
    spray.can.parsing.max-header-name-length = 20
    spray.can.parsing.max-header-value-length = 21
    spray.can.parsing.header-cache.Host = 300""")
  val system = ActorSystem(actorSystemNameFrom(getClass), testConf)

  "The HttpHeaderParser" should {
    "insert the 1st value" in new TestSetup(primed = false) {
      insert("Hello", 'Hello)
      parser.formatRawTrie === "nodes: 0/H, 0/e, 0/l, 0/l, 0/o, 1/Ω\nnodeData: \nvalues: 'Hello"
      parser.formatTrie === "-H-e-l-l-o- 'Hello\n"
    }

    "insert a new branch underneath a simple node" in new TestSetup(primed = false) {
      insert("Hello", 'Hello)
      insert("Hallo", 'Hallo)
      parser.formatRawTrie ===
        """nodes: 0/H, 1/e, 0/l, 0/l, 0/o, 1/Ω, 0/a, 0/l, 0/l, 0/o, 2/Ω
          |nodeData: 6/2/0
          |values: 'Hello, 'Hallo""".stripMargin
      parser.formatTrie ===
        """   ┌─a-l-l-o- 'Hallo
          |-H-e-l-l-o- 'Hello
          |""".stripMargin
    }

    "insert a new branch underneath the root" in new TestSetup(primed = false) {
      insert("Hello", 'Hello)
      insert("Hallo", 'Hallo)
      insert("Yeah", 'Yeah)
      parser.formatRawTrie ===
        """nodes: 2/H, 1/e, 0/l, 0/l, 0/o, 1/Ω, 0/a, 0/l, 0/l, 0/o, 2/Ω, 0/Y, 0/e, 0/a, 0/h, 3/Ω
          |nodeData: 6/2/0, 0/1/11
          |values: 'Hello, 'Hallo, 'Yeah""".stripMargin
      parser.formatTrie ===
        """   ┌─a-l-l-o- 'Hallo
          |-H-e-l-l-o- 'Hello
          | └─Y-e-a-h- 'Yeah
          |""".stripMargin
    }

    "insert a new branch underneath an existing branch node" in new TestSetup(primed = false) {
      insert("Hello", 'Hello)
      insert("Hallo", 'Hallo)
      insert("Yeah", 'Yeah)
      insert("Hoo", 'Hoo)
      parser.formatRawTrie ===
        """nodes: 2/H, 1/e, 0/l, 0/l, 0/o, 1/Ω, 0/a, 0/l, 0/l, 0/o, 2/Ω, 0/Y, 0/e, 0/a, 0/h, 3/Ω, 0/o, 0/o, 4/Ω
          |nodeData: 6/2/16, 0/1/11
          |values: 'Hello, 'Hallo, 'Yeah, 'Hoo""".stripMargin
      parser.formatTrie ===
        """   ┌─a-l-l-o- 'Hallo
          |-H-e-l-l-o- 'Hello
          | | └─o-o- 'Hoo
          | └─Y-e-a-h- 'Yeah
          |""".stripMargin
    }

    "support overriding of previously inserted values" in new TestSetup(primed = false) {
      insert("Hello", 'Hello)
      insert("Hallo", 'Hallo)
      insert("Yeah", 'Yeah)
      insert("Hoo", 'Hoo)
      insert("Hoo", 'Foo)
      parser.formatTrie ===
        """   ┌─a-l-l-o- 'Hallo
          |-H-e-l-l-o- 'Hello
          | | └─o-o- 'Foo
          | └─Y-e-a-h- 'Yeah
          |""".stripMargin
    }

    "prime an empty parser with all defined HeaderValueParsers" in new TestSetup() {
      parser.formatTrie ===
        """     ┌─\r-\n- EmptyHeader
          |     |             ┌─c-h-a-r-s-e-t-:- (Accept-Charset)
          |     |             | └─e-n-c-o-d-i-n-g-:- (Accept-Encoding)
          |   ┌─a-c-c-e-p-t---l-a-n-g-u-a-g-e-:- (Accept-Language)
          |   |   |         |                ┌─\r-\n- Accept: */*
          |   |   |         └─:-(Accept)- -*-/-*-\r-\n- Accept: */*
          |   |   └─u-t-h-o-r-i-z-a-t-i-o-n-:- (Authorization)
          |   | ┌─a-c-h-e---c-o-n-t-r-o-l-:-(Cache-Control)- -m-a-x---a-g-e-=-0-\r-\n- Cache-Control: max-age=0
          |   | |                                             └─n-o---c-a-c-h-e-\r-\n- Cache-Control: no-cache
          | ┌─c-o-n-n-e-c-t-i-o-n-:-(Connection)- -K-e-e-p---A-l-i-v-e-\r-\n- Connection: Keep-Alive
          | |     | |                              | ┌─c-l-o-s-e-\r-\n- Connection: close
          | |     | |                              └─k-e-e-p---a-l-i-v-e-\r-\n- Connection: keep-alive
          | |     | |             ┌─d-i-s-p-o-s-i-t-i-o-n-:- (Content-Disposition)
          | |     | |           ┌─e-n-c-o-d-i-n-g-:- (Content-Encoding)
          | |     | └─t-e-n-t---l-e-n-g-t-h-:-(Content-Length)- -0-\r-\n- Content-Length: 0
          | |     |             └─t-y-p-e-:- (Content-Type)
          | |     └─o-k-i-e-:- (Cookie)
          |-d-a-t-e-:- (Date)
          | |     ┌─e-x-p-e-c-t-:-(Expect)- -1-0-0---c-o-n-t-i-n-u-e-\r-\n- Expect: 100-continue
          | |   ┌─h-o-s-t-:- (Host)
          | | ┌─l-a-s-t---m-o-d-i-f-i-e-d-:- (Last-Modified)
          | | | | └─o-c-a-t-i-o-n-:- (Location)
          | | | └─r-e-m-o-t-e---a-d-d-r-e-s-s-:- (Remote-Address)
          | └─s-e-r-v-e-r-:- (Server)
          |   |   └─t---c-o-o-k-i-e-:- (Set-Cookie)
          |   | ┌─t-r-a-n-s-f-e-r---e-n-c-o-d-i-n-g-:- (Transfer-Encoding)
          |   └─u-s-e-r---a-g-e-n-t-:- (User-Agent)
          |     | ┌─w-w-w---a-u-t-h-e-n-t-i-c-a-t-e-:- (WWW-Authenticate)
          |     └─x---f-o-r-w-a-r-d-e-d---f-o-r-:- (X-Forwarded-For)
          |""".stripMargin
      parser.formatSizes === "363 nodes, 23 nodeData rows, 34 values"
      parser.contentHistogram ===
        Map("Connection" -> 3, "Content-Length" -> 1, "Accept" -> 2, "Cache-Control" -> 2, "Expect" -> 1)
    }

    "retrieve the EmptyHeader" in new TestSetup() {
      parseAndCache("\r\n")() === HttpHeaderParser.EmptyHeader
    }

    "retrieve a cached header with an exact header name match" in new TestSetup() {
      parseAndCache("Connection: close\r\nx")() === Connection("close")
    }

    "retrieve a cached header with a case-insensitive header-name match" in new TestSetup() {
      parseAndCache("Connection: close\r\nx")("coNNection: close\r\nx") === Connection("close")
    }

    "parse and cache a modelled header" in new TestSetup() {
      parseAndCache("Host: spray.io:123\r\nx")("HOST: spray.io:123\r\nx") === Host("spray.io", 123)
    }

    "parse and cache a raw header" in new TestSetup(primed = false) {
      insert("hello: bob", 'Hello)
      val (ixA, headerA) = parseLine("Fancy-Pants: foo\r\nx")
      val (ixB, headerB) = parseLine("Fancy-pants: foo\r\nx")
      parser.formatTrie ===
        """ ┌─f-a-n-c-y---p-a-n-t-s-:-(Fancy-Pants)- -f-o-o-\r-\n- *Fancy-Pants: foo
          |-h-e-l-l-o-:- -b-o-b- 'Hello
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

    "produce an error message for lines with a too-long header name" in new TestSetup() {
      parseLine("123456789012345678901: foo\r\nx") must
        throwA[ParsingException]("HTTP header name exceeds the configured limit of 20 characters")
    }

    "produce an error message for lines with a too-long header value" in new TestSetup() {
      parseLine("foo: 1234567890123456789012\r\nx") must
        throwA[ParsingException]("HTTP header value exceeds the configured limit of 21 characters")
    }

    "continue parsing raw headers even if the overall cache capacity is reached" in new TestSetup() {
      val randomHeaders = Stream.continually {
        val name = nextRandomString(nextRandomTokenChar, nextRandomInt(4, 16))
        val value = nextRandomString(nextRandomPrintableChar, nextRandomInt(4, 16))
        RawHeader(name, value)
      }
      randomHeaders.take(300).foldLeft(0) {
        case (acc, rawHeader) ⇒ acc + parseAndCache(rawHeader.toString + "\r\nx", rawHeader)
      } === 110
      parser.formatSizes === "3070 nodes, 106 nodeData rows, 255 values"
    }

    "continue parsing modelled headers even if the overall cache capacity is reached" in new TestSetup() {
      val randomHostHeaders = Stream.continually {
        Host(
          host = nextRandomString(nextRandomTokenChar, nextRandomInt(4, 8)),
          port = nextRandomInt(1000, 10000))
      }
      randomHostHeaders.take(300).foldLeft(0) {
        case (acc, header) ⇒ acc + parseAndCache(header.toString + "\r\nx", header)
      } === 221
      parser.formatSizes === "3204 nodes, 186 nodeData rows, 255 values"
    }

    "continue parsing raw headers even if the header-specific cache capacity is reached" in new TestSetup() {
      val randomHeaders = Stream.continually {
        val value = nextRandomString(nextRandomPrintableChar, nextRandomInt(4, 16))
        RawHeader("Fancy", value)
      }
      randomHeaders.take(20).foldLeft(0) {
        case (acc, rawHeader) ⇒ acc + parseAndCache(rawHeader.toString + "\r\nx", rawHeader)
      } === 12
    }

    "continue parsing modelled headers even if the header-specific cache capacity is reached" in new TestSetup() {
      val randomHeaders = Stream.continually {
        `User-Agent`(nextRandomString(nextRandomTokenChar, nextRandomInt(4, 16)))
      }
      randomHeaders.take(40).foldLeft(0) {
        case (acc, header) ⇒ acc + parseAndCache(header.toString + "\r\nx", header)
      } === 32
    }
  }

  step(system.shutdown())

  abstract class TestSetup(primed: Boolean = true) extends org.specs2.specification.Scope {
    val parser = HttpHeaderParser(
      settings = ParserSettings(system),
      warnOnIllegalHeader = info ⇒ system.log.warning(info.formatPretty),
      unprimed = !primed)
    def insert(line: String, value: AnyRef): Unit =
      if (parser.isEmpty) parser.insertRemainingCharsAsNewNodes(ByteString(line), value)()
      else parser.insert(ByteString(line), value)()

    def parseLine(line: String) = parser.parseHeaderLine(ByteString(line))() -> parser.resultHeader

    def parseAndCache(lineA: String)(lineB: String = lineA): HttpHeader = {
      val (ixA, headerA) = parseLine(lineA)
      val (ixB, headerB) = parseLine(lineB)
      ixA === ixB
      headerA must beTheSameAs(headerB)
      headerA
    }

    def parseAndCache(line: String, header: HttpHeader): Int = {
      val (ixA, headerA) = parseLine(line)
      val (ixB, headerB) = parseLine(line)
      headerA === header
      headerB === header
      ixA === ixB
      if (headerA eq headerB) 1 else 0
    }

    private[this] val random = new Random(42)
    def nextRandomPrintableChar(): Char = random.nextPrintableChar()
    def nextRandomInt(min: Int, max: Int) = random.nextInt(max - min) + min
    @tailrec final def nextRandomTokenChar(): Char = {
      val c = nextRandomPrintableChar()
      if (CharUtils.isTokenChar(c)) c else nextRandomTokenChar()
    }
    @tailrec final def nextRandomString(charGen: () ⇒ Char, len: Int, sb: JStringBuilder = new JStringBuilder): String =
      if (sb.length < len) nextRandomString(charGen, len, sb.append(charGen())) else sb.toString
  }
}
