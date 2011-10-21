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
import java.io.File
import StatusCodes._
import HttpHeaders._
import java.net.{URL, URLConnection}

private[spray] trait FileAndResourceDirectives {
  this: SimpleDirectives with DetachDirectives =>

  /**
   * Returns a Route that completes GET requests with the content of the given file. The actual I/O operation is
   * running detached in the context of a newly spawned actor, so it doesn't block the current thread.
   * If the file cannot be read the Route completes the request with a "404 NotFound" error.
   */
  def getFromFileName(fileName: String, charset: Option[HttpCharset] = None,
                  resolver: ContentTypeResolver = DefaultContentTypeResolver): Route = {
    getFromFile(new File(fileName), charset, resolver)
  }

  /**
   * Returns a Route that completes GET requests with the content of the given file. The actual I/O operation is
   * running detached in the context of a newly spawned actor, so it doesn't block the current thread.
   * If the file cannot be read the Route completes the request with a "404 NotFound" error.
   */
  def getFromFile(file: File, charset: Option[HttpCharset] = None,
                  resolver: ContentTypeResolver = DefaultContentTypeResolver): Route = {
    detach {
      get { ctx =>
        responseFromFile(file, charset, resolver) match {
          case Some(response) => ctx.complete(response)
          case None => ctx.reject() // reject without specific rejection => same as unmatched "path" directive
        }
      }
    }
  }

  /**
   * Builds an HttpResponse from the content of the given file. If the file cannot be read a "404 NotFound"
   * response is returned. Note that this method is using disk IO which may block the current thread.
   */
  def responseFromFile(file: File, charset: Option[HttpCharset] = None,
                       resolver: ContentTypeResolver = DefaultContentTypeResolver): Option[HttpResponse] = {
    responseFromBuffer(
      lastModified = DateTime(math.min(file.lastModified, System.currentTimeMillis())),
      buffer = FileUtils.readAllBytes(file),
      contentType = resolver(file.getName, charset)
    )
  }

  /**
   * Returns a Route that completes GET requests with the content of the given resource. The actual I/O operation is
   * running detached in the context of a newly spawned actor, so it doesn't block the current thread.
   * If the resource cannot be read the Route completes the request with a "404 NotFound" error.
   */
  def getFromResource(resourceName: String, charset: Option[HttpCharset] = None,
                      resolver: ContentTypeResolver = DefaultContentTypeResolver): Route = {
    detach {
      get { ctx =>
        responseFromResource(resourceName, charset) match {
          case Some(response) => ctx.complete(response)
          case None => ctx.reject() // reject without specific rejection => same as unmatched "path" directive
        }
      }
    }
  }
  
  /**
   * Builds an HttpResponse from the content of the given classpath resource. If the resource cannot be read a
   * "404 NotFound" response is returned. Note that this method is using disk IO which may block the current thread.
   */
  def responseFromResource(resourceName: String, charset: Option[HttpCharset] = None,
                           resolver: ContentTypeResolver = DefaultContentTypeResolver): Option[HttpResponse] = {
    Option(getClass.getClassLoader.getResource(resourceName)).flatMap { resource =>
      val urlConn = resource.openConnection()
      val inputStream = urlConn.getInputStream
      val response = responseFromBuffer(
        lastModified = DateTime(math.min(urlConn.getLastModified, System.currentTimeMillis())),
        buffer = FileUtils.readAllBytes(inputStream),
        contentType = resolver(resourceName, charset)
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
   * current thread. If the file cannot be read the Route completes the request with a "404 NotFound" error.
   */
  def getFromDirectory(directoryName: String, charset: Option[HttpCharset] = None,
                       pathRewriter: String => String = identity,
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
                               pathRewriter: String => String = identity,
                               resolver: ContentTypeResolver = DefaultContentTypeResolver): Route = {
    val base = if (directoryName.isEmpty) "" else directoryName + "/";
    { ctx =>
      val subPath = if (ctx.unmatchedPath.startsWith("/")) ctx.unmatchedPath.substring(1) else ctx.unmatchedPath
      getFromResource(base + pathRewriter(subPath), charset, resolver).apply(ctx)
    }
  }
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