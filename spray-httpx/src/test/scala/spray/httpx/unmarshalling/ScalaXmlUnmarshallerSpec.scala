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
 *
 * These tests have been ported from
 * https://github.com/playframework/playframework/blob/656ee5a56bd7b2c7821d8dcb437688ae1deab1b7/framework/src/play/src/test/scala/play/libs/XMLSpec.scala
 */
package spray.httpx.unmarshalling

import java.io.File

import org.parboiled.common.FileUtils
import org.specs2.execute.PendingUntilFixed
import org.specs2.mutable.Specification
import org.xml.sax.SAXParseException
import spray.http.HttpEntity
import spray.http.MediaTypes._

import scala.xml.NodeSeq

class ScalaXmlUnmarshallerSpec extends Specification with PendingUntilFixed {
  "The ScalaXml (NodeSeq) unmarshaller" should {
    "unmarshal XML bodies" in {
      HttpEntity(`text/xml`, "<int>Hällö</int>").as[NodeSeq].right.get.text === "Hällö"
    }
    "parse XML bodies without loading in a related schema" in {
      withTempFile("I shouldn't be there!") { f ⇒
        val xml = s"""<?xml version="1.0" encoding="ISO-8859-1"?>
                     | <!DOCTYPE foo [
                     |   <!ELEMENT foo ANY >
                     |   <!ENTITY xxe SYSTEM "${f.toURI}">]><foo>hello&xxe;</foo>""".stripMargin

        HttpEntity(`text/xml`, xml).as[NodeSeq].left.get must beMalformedWithSAXParseException
      }
    }
    "parse XML bodies without loading in a related schema from a parameter" in {
      withTempFile("I shouldnt be there!") { generalEntityFile ⇒
        withTempFile {
          s"""<!ENTITY % xge SYSTEM "${generalEntityFile.toURI}">
             |<!ENTITY % pe "<!ENTITY xxe '%xge;'>">""".stripMargin
        } { parameterEntityFile ⇒
          val xml = s"""<?xml version="1.0" encoding="ISO-8859-1"?>
                       | <!DOCTYPE foo [
                       |   <!ENTITY % xpe SYSTEM "${parameterEntityFile.toURI}">
                       |   %xpe;
                       |   %pe;
                       |   ]><foo>hello&xxe;</foo>""".stripMargin
          HttpEntity(`text/xml`, xml).as[NodeSeq].left.get must beMalformedWithSAXParseException
        }
      }
    }
    "gracefully fail when there are too many nested entities" in {
      val nested = for (x ← 1 to 30) yield "<!ENTITY laugh" + x + " \"&laugh" + (x - 1) + ";&laugh" + (x - 1) + ";\">"
      val xml =
        s"""<?xml version="1.0"?>
           | <!DOCTYPE billion [
           | <!ELEMENT billion (#PCDATA)>
           | <!ENTITY laugh0 "ha">
           | ${nested.mkString("\n")}
           | ]>
           | <billion>&laugh30;</billion>""".stripMargin

      HttpEntity(`text/xml`, xml).as[NodeSeq].left.get must beMalformedWithSAXParseException
    }
    "gracefully fail when an entity expands to be very large" in {
      val as = "a" * 50000
      val entities = "&a;" * 50000
      val xml = s"""<?xml version="1.0"?>
                  | <!DOCTYPE kaboom [
                  | <!ENTITY a "$as">
                  | ]>
                  | <kaboom>$entities</kaboom>""".stripMargin
      HttpEntity(`text/xml`, xml).as[NodeSeq].left.get must beMalformedWithSAXParseException
    }
  }

  def beMalformedWithSAXParseException =
    beLike[DeserializationError] {
      case MalformedContent(_, Some(x: SAXParseException)) ⇒ ok
    }

  def withTempFile[T](content: String)(f: File ⇒ T): T = {
    val file = File.createTempFile("xxe", ".txt")
    try {
      FileUtils.writeAllText(content, file)
      f(file)
    } finally {
      file.delete()
    }
  }
}
