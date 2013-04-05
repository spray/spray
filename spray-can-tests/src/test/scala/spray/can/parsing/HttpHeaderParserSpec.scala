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
import spray.util.Utils._

class HttpHeaderParserSpec extends Specification {
  val testConf: Config = ConfigFactory.parseString("""
    akka.event-handlers = ["akka.testkit.TestEventListener"]
    akka.loglevel = WARNING""")
  val system = ActorSystem(actorSystemNameFrom(getClass), testConf)

  "The HttpHeaderParser" should {
    "insert the 1st value" in {
      val parser = emptyParser()
      parser.insertRemainingCharsAsNewNodes("Hello", 'Hello)
      parser.inspectRaw ===
        """nodes: 0/'H, 0/'e, 0/'l, 0/'l, 1/'o
          |nodeData: 0/-1/0
          |values: 'Hello""".stripMargin
      parser.inspect === "-H-e-l-l-o 'Hello\n"
    }

    "insert a new branch underneath a simple node" in {
      val parser = emptyParser()
      parser.insertRemainingCharsAsNewNodes("Hello", 'Hello)
      parser.insert("Hallo", 'Hallo)
      parser.inspectRaw ===
        """nodes: 0/'H, 2/'e, 0/'l, 0/'l, 1/'o, 0/'a, 0/'l, 0/'l, 3/'o
          |nodeData: 0/-1/0, 5/2/0, 0/-2/0
          |values: 'Hello, 'Hallo""".stripMargin
      parser.inspect ===
        """   ┌─a-l-l-o 'Hallo
          |-H-e-l-l-o 'Hello
          |""".stripMargin
    }

    "insert a new branch underneath the root" in {
      val parser = emptyParser()
      parser.insertRemainingCharsAsNewNodes("Hello", 'Hello)
      parser.insert("Hallo", 'Hallo)
      parser.insert("Yeah", 'Yeah)
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

    "insert a new branch underneath an existing branch node" in {
      val parser = emptyParser()
      parser.insertRemainingCharsAsNewNodes("Hello", 'Hello)
      parser.insert("Hallo", 'Hallo)
      parser.insert("Yeah", 'Yeah)
      parser.insert("Hoo", 'Hoo)
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

    "support overriding of previously inserted values" in {
      val parser = emptyParser()
      parser.insertRemainingCharsAsNewNodes("Hello", 'Hello)
      parser.insert("Hallo", 'Hallo)
      parser.insert("Yeah", 'Yeah)
      parser.insert("Hoo", 'Hoo)
      parser.insert("Hoo", 'Foo)
      parser.inspect ===
        """   ┌─a-l-l-o 'Hallo
          |-H-e-l-l-o 'Hello
          | | └─o-o 'Foo
          | └─Y-e-a-h 'Yeah
          |""".stripMargin
    }

    "prime an empty parser with all defined HeaderValueParsers" in {
      val parser = primedParser()
      parser.inspect ===
        """   ┌─\r-\n EmptyHeader
          |   |             ┌─c-h-a-r-s-e-t-: accept-charset
          |   |             | └─e-n-c-o-d-i-n-g-: accept-encoding
          | ┌─a-c-c-e-p-t---l-a-n-g-u-a-g-e-: accept-language
          | |   |         | └─r-a-n-g-e-s-: accept-ranges
          | |   |         |               ┌─\r-\n Accept: */*
          | |   |         └─:(accept)- -*-/-*-\r-\n Accept: */*
          | |   └─u-t-h-o-r-i-z-a-t-i-o-n-: authorization
          | | ┌─a-c-h-e---c-o-n-t-r-o-l-:(cache-control)- -m-a-x---a-g-e-=-0-\r-\n Cache-Control: max-age=0
          | | |     ┌─n-e-c-t-i-o-n-:(connection)- -K-e-e-p---A-l-i-v-e-\r-\n Connection: Keep-Alive
          | | |     |                               | ┌─c-l-o-s-e-\r-\n Connection: close
          | | |     |                               └─k-e-e-p---a-l-i-v-e-\r-\n Connection: keep-alive
          | | |     |         ┌─d-i-s-p-o-s-i-t-i-o-n-: content-disposition
          | | | ┌─n-t-e-n-t---e-n-c-o-d-i-n-g-: content-encoding
          | | | |             | ┌─l-e-n-g-t-h-: content-length
          | | | |             └─t-y-p-e-: content-type
          |-c-o-o-k-i-e-: cookie
          | |     ┌─d-a-t-e-: date
          | |   ┌─h-o-s-t-: host
          | | ┌─l-a-s-t---m-o-d-i-f-i-e-d-: last-modified
          | | | | └─o-c-a-t-i-o-n-: location
          | | | └─r-e-m-o-t-e---a-d-d-r-e-s-s-: remote-address
          | └─s-e-r-v-e-r-: server
          |   |   └─t---c-o-o-k-i-e-: set-cookie
          |   | ┌─t-r-a-n-s-f-e-r---e-n-c-o-d-i-n-g-: transfer-encoding
          |   └─u-s-e-r---a-g-e-n-t-: user-agent
          |     | ┌─w-w-w---a-u-t-h-e-n-t-i-c-a-t-e-: www-authenticate
          |     └─x---f-o-r-w-a-r-d-e-d---f-o-r-: x-forwarded-for
          |""".stripMargin
      parser.inspectSizes === "300 nodes, 52 nodeData rows, 31 values"
    }

    "retrieve a cached header with an exact header name match" in {
      val parser = primedParser()
      val (headerA, ixA) = parser.parseHeaderLine("Connection: close\r\n")
      val (headerB, ixB) = parser.parseHeaderLine("Connection: close\r\n")
      ixA === ixB
      headerA must beTheSameAs(headerB)
    }

    "retrieve a cached header with a case-insensitive header-name match" in {
      val parser = primedParser()
      val (headerA, ixA) = parser.parseHeaderLine("Connection: close\r\n")
      val (headerB, ixB) = parser.parseHeaderLine("coNNection: close\r\n")
      ixA === ixB
      headerA must beTheSameAs(headerB)
    }
  }

  def emptyParser() = HttpHeaderParser(ParserSettings(system), info ⇒ system.log.warning(info.formatPretty))
  def primedParser() = HttpHeaderParser.prime(emptyParser())

  step(system.shutdown())
}
