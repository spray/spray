/*
 * Copyright (C) 2011 Mathias Doenitz
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

package cc.spray
package typeconversion

import http._
import MediaTypes._
import MediaRanges._
import collection.JavaConverters._
import java.io.{ByteArrayOutputStream, ByteArrayInputStream}
import org.parboiled.common.FileUtils
import HttpHeaders._
import HttpCharsets._
import org.jvnet.mimepull.{MIMEConfig, MIMEMessage}
import util._

trait MultipartUnmarshallers {

  implicit val MultipartContentUnmarshaller = new SimpleUnmarshaller[MultipartContent] {
    val canUnmarshalFrom = ContentTypeRange(`multipart/*`) :: Nil
    val mimeParsingConfig = make(new MIMEConfig()) {
      _.setMemoryThreshold(-1) // use only in-memory parsing
    }

    def unmarshal(content: HttpContent) = {
      content.contentType.mediaType.asInstanceOf[MultipartMediaType].boundary match {
        case Some(boundary) =>
          try {
            Right(parseBodyParts(content.buffer, boundary))
          } catch {
            case e: Exception => Left(MalformedContent("Could not parse multipart content: " + e.getMessage))
          }
        case None => Left(MalformedContent("Content-Type with a multipart media type must have a 'boundary' parameter"))
      }
    }

    def parseBodyParts(buf: Array[Byte], boundary: String): MultipartContent = {
      val mimeMsg = new MIMEMessage(new ByteArrayInputStream(buf), boundary, mimeParsingConfig)
      MultipartContent {
        mimeMsg.getAttachments.asScala.map { part =>
          val headers: List[HttpHeader] = part.getAllHeaders.asScala.map { header =>
            HttpHeader(header.getName, header.getValue)
          } (collection.breakOut)
          BodyPart(
            headers = headers,
            content = HttpContent(
              contentType = {
                headers.collect {
                  case `Content-Type`(t) => t
                }.headOption.getOrElse(ContentType(`text/plain`, `US-ASCII`)) // RFC 2046 section 5.1
              },
              buffer = {
                val outputStream = new ByteArrayOutputStream()
                FileUtils.copyAll(part.readOnce(), outputStream)
                outputStream.toByteArray
              }
            )
          )
        } (collection.breakOut)
      }
    }
  }

  implicit val MultipartFormDataUnmarshaller = new SimpleUnmarshaller[MultipartFormData] {
    val canUnmarshalFrom = ContentTypeRange(new `multipart/form-data`()) :: Nil

    def unmarshal(content: HttpContent) = {
      MultipartContentUnmarshaller(content).right.flatMap { mpContent =>
        try {
          Right(MultipartFormData(mpContent.parts.map(part => nameOf(part) -> part)(collection.breakOut)))
        } catch {
          case e: Exception => Left(MalformedContent("Illegal multipart/form-data content: " + e.getMessage))
        }
      }
    }

    def nameOf(part: BodyPart): String = {
      part.headers.mapFind {
        case `Content-Disposition`("form-data", parms) => parms.get("name")
        case _ => None
      }.getOrElse(throw new RuntimeException("unnamed body part (no Content-Disposition header or no 'name' parameter)"))
    }
  }

}

object MultipartUnmarshallers extends MultipartUnmarshallers