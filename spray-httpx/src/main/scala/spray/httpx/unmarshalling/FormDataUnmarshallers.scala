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

import java.io.{ ByteArrayOutputStream, ByteArrayInputStream }
import org.jvnet.mimepull.{ MIMEMessage, MIMEConfig }
import org.parboiled.common.FileUtils
import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import spray.http.parser.HttpParser
import spray.util._
import spray.http._
import MediaTypes._
import MediaRanges._
import HttpHeaders._
import HttpCharsets._

trait FormDataUnmarshallers {

  private[this] val mimeParsingConfig = {
    val config = new MIMEConfig
    config.setMemoryThreshold(-1) // use only in-memory parsing
    config
  }

  private def convertMimeMessage(mimeMsg: MIMEMessage, defaultCharset: HttpCharset): Seq[BodyPart] = {
    mimeMsg.getAttachments.asScala.map { part ⇒
      val rawHeaders: List[HttpHeader] =
        part.getAllHeaders.asScala.map(h ⇒ RawHeader(h.getName, h.getValue))(collection.breakOut)
      HttpParser.parseHeaders(rawHeaders) match {
        case (Nil, headers) ⇒
          val contentType = headers.mapFind { case `Content-Type`(t) ⇒ Some(t); case _ ⇒ None }
            .getOrElse(ContentType(`text/plain`, defaultCharset)) // RFC 2046 section 5.1
          val outputStream = new ByteArrayOutputStream
          FileUtils.copyAll(part.readOnce(), outputStream)
          BodyPart(HttpEntity(contentType, outputStream.toByteArray), headers)
        case (errors, _) ⇒ sys.error("Multipart part contains %s illegal header(s):\n%s"
          .format(errors.size, errors.mkString("\n")))
      }
    }(collection.breakOut)
  }

  implicit val MultipartContentUnmarshaller = multipartContentUnmarshaller(`UTF-8`)
  def multipartContentUnmarshaller(defaultCharset: HttpCharset): Unmarshaller[MultipartContent] =
    Unmarshaller[MultipartContent](`multipart/*`) {
      case HttpEntity.NonEmpty(contentType, data) ⇒
        contentType.mediaType.parameters.get("boundary") match {
          case None | Some("") ⇒
            sys.error("Content-Type with a multipart media type must have a non-empty 'boundary' parameter")
          case Some(boundary) ⇒
            val mimeMsg = new MIMEMessage(new ByteArrayInputStream(data.toByteArray), boundary, mimeParsingConfig)
            MultipartContent(convertMimeMessage(mimeMsg, defaultCharset))
        }
      case HttpEntity.Empty ⇒ MultipartContent.Empty
    }

  implicit val MultipartFormDataUnmarshaller = new SimpleUnmarshaller[MultipartFormData] {
    val canUnmarshalFrom = ContentTypeRange(`multipart/form-data`) :: Nil

    def unmarshal(entity: HttpEntity) =
      MultipartContentUnmarshaller(entity).right.flatMap { mpContent ⇒
        try {
          checkValid(mpContent.parts)
          Right(MultipartFormData(mpContent.parts))
        } catch {
          case NonFatal(ex) ⇒
            Left(MalformedContent("Illegal multipart/form-data content: " + ex.getMessage.nullAsEmpty, ex))
        }
      }

    def checkValid(parts: Seq[BodyPart]): Unit = {
      parts.foreach { part ⇒
        if (part.headers.map(_.lowercaseName).toSet.size != part.headers.size)
          sys.error("duplicate header names")
      }
      val contentDispositionNames = parts.map(_.headers.mapFind {
        case `Content-Disposition`("form-data", parms) ⇒ parms.get("name")
        case _                                         ⇒ None
      } getOrElse sys.error("unnamed body part (no Content-Disposition header or no 'name' parameter)"))
      if (contentDispositionNames.size != contentDispositionNames.toSet.size)
        sys.error("duplicate 'name' parameter values in Content-Disposition headers")
    }
  }

  implicit val UrlEncodedFormDataUnmarshaller: Unmarshaller[FormData] = urlEncodedFormDataUnmarshaller(`UTF-8`)
  def urlEncodedFormDataUnmarshaller(defaultCharset: HttpCharset): Unmarshaller[FormData] =
    Unmarshaller[FormData](`application/x-www-form-urlencoded`) {
      case HttpEntity.Empty ⇒ FormData.Empty
      case entity: HttpEntity.NonEmpty ⇒
        val data = entity.asString(defaultCharset)
        try {
          val query = Uri.Query(data, entity.contentType.definedCharset.getOrElse(defaultCharset).nioCharset)
          FormData(query.toMap)
        } catch {
          case ex: IllegalUriException ⇒
            throw new IllegalArgumentException(ex.info.formatPretty.replace("Query,", "form content,"))
        }
    }
}

object FormDataUnmarshallers extends FormDataUnmarshallers
