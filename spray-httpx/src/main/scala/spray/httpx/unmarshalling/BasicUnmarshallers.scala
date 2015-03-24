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

package spray.httpx.unmarshalling

import java.nio.ByteBuffer
import java.io.{ InputStreamReader, ByteArrayInputStream }
import javax.xml.XMLConstants
import javax.xml.parsers.{ SAXParser, SAXParserFactory }
import scala.xml.{ XML, NodeSeq }
import spray.http._
import MediaTypes._

trait BasicUnmarshallers {

  implicit val ByteArrayUnmarshaller = new Unmarshaller[Array[Byte]] {
    def apply(entity: HttpEntity) = Right(entity.data.toByteArray)
  }

  implicit val CharArrayUnmarshaller = new Unmarshaller[Array[Char]] {
    def apply(entity: HttpEntity) = Right { // we can convert anything to a char array
      entity match {
        case HttpEntity.NonEmpty(contentType, data) ⇒
          val charBuffer = contentType.charset.nioCharset.decode(ByteBuffer.wrap(data.toByteArray))
          val array = new Array[Char](charBuffer.length())
          charBuffer.get(array)
          array
        case HttpEntity.Empty ⇒ Array.empty[Char]
      }
    }
  }

  implicit val StringUnmarshaller = new Unmarshaller[String] {
    def apply(entity: HttpEntity) = Right(entity.asString)
  }

  //# nodeseq-unmarshaller
  implicit val NodeSeqUnmarshaller =
    Unmarshaller[NodeSeq](`text/xml`, `application/xml`, `text/html`, `application/xhtml+xml`) {
      case HttpEntity.NonEmpty(contentType, data) ⇒
        XML.withSAXParser(createSAXParser())
          .load(new InputStreamReader(new ByteArrayInputStream(data.toByteArray), contentType.charset.nioCharset))
      case HttpEntity.Empty ⇒ NodeSeq.Empty
    }
  //#

  /**
   * Provides a SAXParser for the NodeSeqUnmarshaller to use. Override to provide a custom SAXParser implementation.
   * Will be called once for for every request to be unmarshalled. The default implementation calls [[createSaferSAXParser]].
   * @return
   */
  protected def createSAXParser(): SAXParser = createSaferSAXParser()

  /** Creates a safer SAXParser. */
  protected def createSaferSAXParser(): SAXParser = {
    val factory = SAXParserFactory.newInstance()
    import com.sun.org.apache.xerces.internal.impl.Constants
    import javax.xml.XMLConstants

    factory.setFeature(Constants.SAX_FEATURE_PREFIX + Constants.EXTERNAL_GENERAL_ENTITIES_FEATURE, false)
    factory.setFeature(Constants.SAX_FEATURE_PREFIX + Constants.EXTERNAL_PARAMETER_ENTITIES_FEATURE, false)
    factory.setFeature(Constants.XERCES_FEATURE_PREFIX + Constants.DISALLOW_DOCTYPE_DECL_FEATURE, true)
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    val parser = factory.newSAXParser()
    try {
      parser.setProperty("http://apache.org/xml/properties/locale", java.util.Locale.ROOT)
    } catch {
      case e: org.xml.sax.SAXNotRecognizedException ⇒ // property is not needed
    }
    parser
  }
}

object BasicUnmarshallers extends BasicUnmarshallers
