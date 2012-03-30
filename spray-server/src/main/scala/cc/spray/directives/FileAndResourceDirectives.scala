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
package directives

import http._
import org.parboiled.common.FileUtils
import StatusCodes._
import HttpHeaders._
import utils.identityFunc
import java.io.{FileInputStream, File}
import java.util.Arrays
import typeconversion.{DefaultMarshallers, SimpleMarshaller}

private[spray] trait FileAndResourceDirectives {
  this: SimpleDirectives with ExecutionDirectives with MiscDirectives with DefaultMarshallers =>

  /**
   * Returns a Route that completes GET requests with the content of the given file. The actual I/O operation is
   * running detached in the context of a newly spawned actor, so it doesn't block the current thread (but potentially
   * some other thread !).
   * If the file cannot be found or read the Route rejects the request.
   */
  def getFromFileName(fileName: String, charset: Option[HttpCharset] = None,
                  resolver: ContentTypeResolver = DefaultContentTypeResolver): Route = {
    getFromFile(new File(fileName), charset, resolver)
  }

  /**
   * Returns a Route that completes GET requests with the content of the given file. The actual I/O operation is
   * running detached in the context of a newly spawned actor, so it doesn't block the current thread (but potentially
   * some other thread !).
   * If the file cannot be found or read the Route rejects the request.
   */
  def getFromFile(file: File, charset: Option[HttpCharset] = None,
                  resolver: ContentTypeResolver = DefaultContentTypeResolver): Route = {
    def addLastModifiedHeader(response: HttpResponse) = response.withHeadersTransformed { headers =>
      if (headers.exists(_.isInstanceOf[`Last-Modified`])) headers
      else `Last-Modified`(validateLastModified(file.lastModified)) :: headers
    }

    detach {
      transformResponse(addLastModifiedHeader) {
        get { ctx =>
          if (file.isFile && file.canRead) {
            val contentType = resolver(file.getName, charset)
            if (file.length() >= SprayServerSettings.FileChunkingThresholdSize) {
              import FileChunking._
              implicit val marshaller = fileChunkMarshaller(contentType)
              ctx.complete(fileChunkStream(file))
            } else ctx.complete(responseFromFile(file, contentType).get)
          } else ctx.reject() // reject without specific rejection => same as unmatched "path" directive
        }
      }
    }
  }

  /**
   * Builds an HttpResponse from the content of the given file. If the file cannot be read the method returns `None`.
   * Note that this method is using disk IO which may block the current thread.
   */
  def responseFromFile(file: File, contentType: ContentType): Option[HttpResponse] = {
    responseFromBuffer(
      lastModified = validateLastModified(file.lastModified),
      buffer = FileUtils.readAllBytes(file),
      contentType = contentType
    )
  }

  /**
   * Returns a Route that completes GET requests with the content of the given resource. The actual I/O operation is
   * running detached in the context of a newly spawned actor, so it doesn't block the current thread (but potentially
   * some other thread !).
   * If the file cannot be found or read the Route rejects the request.
   */
  def getFromResource(resourceName: String, charset: Option[HttpCharset] = None,
                      resolver: ContentTypeResolver = DefaultContentTypeResolver): Route = {
    if (!resourceName.endsWith("/")) {
      detach {
        get { ctx =>
          responseFromResource(resourceName, resolver(resourceName, charset)) match {
            case Some(response) => ctx.complete(response)
            case None => ctx.reject() // reject without specific rejection => same as unmatched "path" directive
          }
        }
      }
    } else _.reject() // don't serve the content of directories
  }
  
  /**
   * Builds an HttpResponse from the content of the given resource. If the resource cannot be read the method returns
   * `None`. Note that this method is using disk IO which may block the current thread.
   */
  def responseFromResource(resourceName: String, contentType: => ContentType): Option[HttpResponse] = {
    Option(getClass.getClassLoader.getResource(resourceName)).flatMap { resource =>
      val urlConn = resource.openConnection()
      val inputStream = urlConn.getInputStream
      val response = responseFromBuffer(
        lastModified = validateLastModified(urlConn.getLastModified),
        buffer = FileUtils.readAllBytes(inputStream),
        contentType = contentType
      )
      inputStream.close()
      response
    }
  }
  
  private def responseFromBuffer(lastModified: DateTime, buffer: Array[Byte],
                                 contentType: => ContentType): Option[HttpResponse] = {
    Option(buffer).map { buffer =>
      HttpResponse(OK, List(`Last-Modified`(lastModified)), HttpContent(contentType, buffer))
    }
  }

  /**
   * Returns a Route that completes GET requests with the content of a file underneath the given directory.
   * The unmatchedPath of the [[cc.spray.RequestContext]] is first transformed by the given pathRewriter function before
   * being appended to the given directoryName to build the final fileName. 
   * The actual I/O operation is running detached in the context of a newly spawned actor, so it doesn't block the
   * current thread. If the file cannot be read the Route rejects the request.
   */
  def getFromDirectory(directoryName: String, charset: Option[HttpCharset] = None,
                       pathRewriter: String => String = identityFunc,
                       resolver: ContentTypeResolver = DefaultContentTypeResolver): Route = {
    val base = if (directoryName.endsWith("/")) directoryName else directoryName + "/";
    { ctx =>
      val subPath = if (ctx.unmatchedPath.startsWith("/")) ctx.unmatchedPath.substring(1) else ctx.unmatchedPath
      getFromFileName(base + pathRewriter(subPath), charset, resolver).apply(ctx)
    }
  }

  /**
   * Same as "getFromDirectory" except that the file is not fetched from the file system but rather from a
   * "resource directory". 
   */
  def getFromResourceDirectory(directoryName: String, charset: Option[HttpCharset] = None,
                               pathRewriter: String => String = identityFunc,
                               resolver: ContentTypeResolver = DefaultContentTypeResolver): Route = {
    val base =
      if (directoryName.isEmpty) ""
      else if (directoryName.endsWith("/")) directoryName
      else directoryName + "/";
    { ctx =>
      val subPath = if (ctx.unmatchedPath.startsWith("/")) ctx.unmatchedPath.substring(1) else ctx.unmatchedPath
      getFromResource(base + pathRewriter(subPath), charset, resolver).apply(ctx)
    }
  }

  private def validateLastModified(timestamp: Long) = DateTime(math.min(timestamp, System.currentTimeMillis))
}

object DefaultContentTypeResolver extends ContentTypeResolver {
  def apply(fileName: String, charset: Option[HttpCharset]): ContentType = {
    val mimeType = MediaTypes.forExtension(extension(fileName)).getOrElse(MediaTypes.`application/octet-stream`)
    charset match {
      case Some(cs) => ContentType(mimeType, cs)
      case None => ContentType(mimeType)
    }
  }

  def extension(fileName: String) = fileName.lastIndexOf('.') match {
    case -1 => ""
    case x => fileName.substring(x + 1)
  }
}

object FileChunking {
  case class FileChunk(buffer: Array[Byte])

  def fileChunkStream(file: File): Stream[FileChunk] = {
    val fis = new FileInputStream(file)

    def chunkStream(): Stream[FileChunk] = {
      val buffer = new Array[Byte](SprayServerSettings.FileChunkingChunkSize)
      val bytesRead = fis.read(buffer)
      if (bytesRead > 0) {
        val chunkBytes = if (bytesRead == buffer.length) buffer else Arrays.copyOfRange(buffer, 0, bytesRead)
        Stream.cons(FileChunk(chunkBytes), chunkStream())
      } else Stream.Empty
    }
    chunkStream()
  }

  def fileChunkMarshaller(contentType: ContentType) = new SimpleMarshaller[FileChunk] {
    val canMarshalTo = contentType :: Nil
    def marshal(value: FileChunk, contentType: ContentType) = HttpContent(contentType, value.buffer)
  }
}