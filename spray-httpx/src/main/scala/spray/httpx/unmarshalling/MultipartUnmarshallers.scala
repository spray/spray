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

package spray.httpx.unmarshalling

import java.io.{ ByteArrayOutputStream, ByteArrayInputStream }
import org.jvnet.mimepull.{ MIMEMessage, MIMEConfig }
import org.parboiled.common.FileUtils
import scala.collection.JavaConverters._
import akka.util.NonFatal
import spray.http.parser.HttpParser
import spray.util._
import spray.http._
import MediaTypes._
import MediaRanges._
import HttpHeaders._
import HttpCharsets._

trait MultipartUnmarshallers {

  private[this] val mimeParsingConfig = {
    val config = new MIMEConfig
    config.setMemoryThreshold(-1) // use only in-memory parsing
    config
  }

  private def convertMimeMessage(mimeMsg: MIMEMessage): Seq[BodyPart] = {
    mimeMsg.getAttachments.asScala.map { part ⇒
      val rawHeaders: List[HttpHeader] =
        part.getAllHeaders.asScala.map(h ⇒ RawHeader(h.getName, h.getValue))(collection.breakOut)
      HttpParser.parseHeaders(rawHeaders) match {
        case (Nil, headers) ⇒
          val contentType = headers.mapFind { case `Content-Type`(t) ⇒ Some(t); case _ ⇒ None }
            .getOrElse(ContentType(`text/plain`, `US-ASCII`)) // RFC 2046 section 5.1
          val outputStream = new ByteArrayOutputStream
          FileUtils.copyAll(part.readOnce(), outputStream)
          BodyPart(HttpEntity(contentType, outputStream.toByteArray), headers)
        case (errors, _) ⇒ sys.error("Multipart part contains %s illegal header(s):\n%s"
          .format(errors.size, errors.mkString("\n")))
      }
    }(collection.breakOut)
  }

  implicit val MultipartContentUnmarshaller = Unmarshaller[MultipartContent](`multipart/*`) {
    case HttpBody(contentType, buffer) ⇒
      contentType.mediaType.asInstanceOf[MultipartMediaType].boundary match {
        case Some(boundary) ⇒
          val mimeMsg = new MIMEMessage(new ByteArrayInputStream(buffer), boundary, mimeParsingConfig)
          MultipartContent(convertMimeMessage(mimeMsg))
        case None ⇒ sys.error("Content-Type with a multipart media type must have a 'boundary' parameter")
      }
    case EmptyEntity ⇒ MultipartContent.Empty
  }

  implicit val MultipartFormDataUnmarshaller = new SimpleUnmarshaller[MultipartFormData] {
    val canUnmarshalFrom = ContentTypeRange(`multipart/form-data`) :: Nil

    def unmarshal(entity: HttpEntity) =
      MultipartContentUnmarshaller(entity).right.flatMap { mpContent ⇒
        try Right(MultipartFormData(mpContent.parts.map(part ⇒ nameOf(part) -> part)(collection.breakOut)))
        catch {
          case NonFatal(ex) ⇒
            Left(MalformedContent("Illegal multipart/form-data content: " + ex.getMessage.nullAsEmpty, ex))
        }
      }

    def nameOf(part: BodyPart): String =
      part.headers.mapFind {
        case `Content-Disposition`("form-data", parms) ⇒ parms.get("name")
        case _                                         ⇒ None
      } getOrElse sys.error("unnamed body part (no Content-Disposition header or no 'name' parameter)")
  }
}

object MultipartUnmarshallers extends MultipartUnmarshallers
