/*
 * Copyright © 2011-2013 the spray project <http://spray.io>
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

import spray.http.HttpCharsets._
import spray.http._
import spray.http.MediaTypes._
import scala.collection.JavaConverters._
import org.jvnet.mimepull.{ MIMEConfig, MIMEMessage }
import java.io.{ ByteArrayOutputStream, ByteArrayInputStream }
import spray.http.HttpHeaders.`Content-Type`
import spray.http.parser.HttpParser
import org.parboiled.common.FileUtils
import spray.http.BodyPart
import spray.http.HttpHeaders.RawHeader
import scala.Some
import spray.util._

/**
 * @author Daniel
 */
trait ByteRangesUnmarshallers {

  private[this] val mimeParsingConfig = {
    val config = new MIMEConfig
    config.setMemoryThreshold(-1) // use only in-memory parsing
    config
  }

  implicit val MultiparByterangesUnmarshaller = multipartByterangesUnmarshaller(`UTF-8`)

  def multipartByterangesUnmarshaller(defaultCharset: HttpCharset): Unmarshaller[MultipartByteRanges] =
    Unmarshaller[MultipartByteRanges](`multipart/byteranges`) {
      case HttpEntity.NonEmpty(contentType, data) ⇒
        contentType.mediaType.parameters.get("boundary") match {
          case None | Some("") ⇒
            sys.error("Content-Type with a multipart/byteranges media type must have a non-empty 'boundary' parameter")
          case Some(boundary) ⇒
            val mimeMsg = new MIMEMessage(new ByteArrayInputStream(data.toByteArray), boundary, mimeParsingConfig)
            MultipartByteRanges(convertMimeMessage(mimeMsg, defaultCharset))
        }
      case HttpEntity.Empty ⇒ MultipartByteRanges.Empty
    }

  private def convertMimeMessage(mimeMsg: MIMEMessage, defaultCharset: HttpCharset): Seq[ByteRangePart] = {
    mimeMsg.getAttachments.asScala.map { part ⇒
      val rawHeaders: List[HttpHeader] =
        part.getAllHeaders.asScala.map(h ⇒ RawHeader(h.getName, h.getValue))(collection.breakOut)
      HttpParser.parseHeaders(rawHeaders) match {
        case (Nil, headers) ⇒
          val contentType = headers.mapFind { case `Content-Type`(t) ⇒ Some(t); case _ ⇒ None }
            .getOrElse(ContentType(`text/plain`, defaultCharset)) // RFC 2046 section 5.1
          val outputStream = new ByteArrayOutputStream
          FileUtils.copyAll(part.readOnce(), outputStream)
          ByteRangePart(HttpEntity(contentType, outputStream.toByteArray), headers)
        case (errors, _) ⇒ sys.error("Multipart part contains %s illegal header(s):\n%s"
          .format(errors.size, errors.mkString("\n")))
      }
    }(collection.breakOut)
  }

}

object ByteRangesUnmarshallers extends ByteRangesUnmarshallers