/*
 * Copyright (C) 2011-2012 spray.io
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

package spray.routing
package directives

import java.io.File
import java.net.{URL, URLConnection}
import org.parboiled.common.FileUtils
import akka.actor.ActorRefFactory
import spray.httpx.marshalling.BasicMarshallers
import spray.util._
import spray.http._
import HttpHeaders._
import shapeless._


trait FileAndResourceDirectives {
  import BasicDirectives._
  import ExecutionDirectives._
  import MethodDirectives._
  import RespondWithDirectives._
  import RouteDirectives._

  /**
   * A Route that completes GET requests with the content of the given file. The actual I/O operation is
   * running detached in the context of a newly spawned actor, so it doesn't block the current thread (but potentially
   * some other thread !). If the file cannot be found or read the request is rejected.
   */
  def getFromFileName(fileName: String, charset: Option[HttpCharset] = None)
                     (implicit settings: RoutingSettings, resolver: ContentTypeResolver,
                      refFactory: ActorRefFactory): Route =
    getFromFile(new File(fileName), charset)

  /**
   * A Route that completes GET requests with the content of the given file. The actual I/O operation is
   * running detached in the context of a newly spawned actor, so it doesn't block the current thread (but potentially
   * some other thread !). If the file cannot be found or read the request is rejected.
   */
  def getFromFile(file: File, charset: Option[HttpCharset] = None)
                 (implicit settings: RoutingSettings, resolver: ContentTypeResolver,
                  refFactory: ActorRefFactory): Route = {
    (get & detachTo(singleRequestServiceActor) & respondWithLastModifiedHeader(file.lastModified)) { ctx =>
      if (file.isFile && file.canRead) {
        implicit val bufferMarshaller = BasicMarshallers.byteArrayMarshaller(resolver(file.getName, charset))
        if (file.length >= settings.FileChunkingThresholdSize)
          ctx.complete(file.toByteArrayStream(settings.FileChunkingChunkSize.toInt))
        else ctx.complete(FileUtils.readAllBytes(file))
      } else ctx.reject() // reject without specific rejection => same as unmatched "path" directive
    }
  }

  /**
   * Adds a Last-Modified header to all HttpResponses from its inner Route.
   */
  def respondWithLastModifiedHeader(timestamp: Long): Directive0 =
    respondWithHeader(`Last-Modified`(DateTime(math.min(timestamp, System.currentTimeMillis))))

  /**
   * Returns a Route that completes GET requests with the content of the given resource. The actual I/O operation is
   * running detached in the context of a newly spawned actor, so it doesn't block the current thread (but potentially
   * some other thread !).
   * If the file cannot be found or read the Route rejects the request.
   */
  def getFromResource(resourceName: String, charset: Option[HttpCharset] = None)
                     (implicit resolver: ContentTypeResolver, refFactory: ActorRefFactory): Route = {
    if (!resourceName.endsWith("/")) {
      def resource = getClass.getClassLoader.getResource(resourceName)
      (get & detachTo(singleRequestServiceActor) & provide(Option(resource)))
        .flatMap(openConnection) { urlConn =>
          implicit val bufferMarshaller = BasicMarshallers.byteArrayMarshaller(resolver(resourceName, charset))
          respondWithLastModifiedHeader(urlConn.getLastModified) {
            complete(FileUtils.readAllBytes(urlConn.getInputStream))
          }
        }
    } else reject() // don't serve the content of directories
  }

  private def openConnection: Option[URL] :: HNil => Directive[URLConnection :: HNil] = {
    case Some(url) :: HNil => provide(url.openConnection())
    case _ => reject()
  }

  /**
   * Returns a Route that completes GET requests with the content of a file underneath the given directory.
   * The unmatchedPath of the [[spray.RequestContext]] is first transformed by the given pathRewriter function before
   * being appended to the given directoryName to build the final fileName.
   * The actual I/O operation is running detached in the context of a newly spawned actor, so it doesn't block the
   * current thread. If the file cannot be read the Route rejects the request.
   */
  def getFromDirectory(directoryName: String, charset: Option[HttpCharset] = None)
                      (implicit settings: RoutingSettings, resolver: ContentTypeResolver,
                       refFactory: ActorRefFactory): Route = {
    val base = if (directoryName.endsWith("/")) directoryName else directoryName + "/"
    Route { ctx =>
      val subPath = if (ctx.unmatchedPath.startsWith("/")) ctx.unmatchedPath.substring(1) else ctx.unmatchedPath
      getFromFileName(base + subPath, charset).apply(ctx)
    }
  }

  /**
   * Same as "getFromDirectory" except that the file is not fetched from the file system but rather from a
   * "resource directory".
   */
  def getFromResourceDirectory(directoryName: String, charset: Option[HttpCharset] = None)
                              (implicit resolver: ContentTypeResolver, refFactory: ActorRefFactory): Route = {
    val base =
      if (directoryName.isEmpty) ""
      else if (directoryName.endsWith("/")) directoryName
      else directoryName + "/"
    Route { ctx =>
      val subPath = if (ctx.unmatchedPath.startsWith("/")) ctx.unmatchedPath.substring(1) else ctx.unmatchedPath
      getFromResource(base + subPath, charset).apply(ctx)
    }
  }

}

object FileAndResourceDirectives extends FileAndResourceDirectives


trait ContentTypeResolver {
  def apply(fileName: String, charset: Option[HttpCharset]): ContentType
}

object ContentTypeResolver {
  implicit val Default = new ContentTypeResolver {
    def apply(fileName: String, charset: Option[HttpCharset]) = {
      val ext = fileName.lastIndexOf('.') match {
        case -1 => ""
        case x => fileName.substring(x + 1)
      }
      ContentType(
        mediaType = MediaTypes.forExtension(ext).getOrElse(MediaTypes.`application/octet-stream`),
        definedCharset = charset
      )
    }
  }
}