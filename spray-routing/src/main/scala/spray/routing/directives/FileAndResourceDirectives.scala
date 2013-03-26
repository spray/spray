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
import scala.annotation.tailrec
import akka.actor.ActorRefFactory
import shapeless._
import spray.httpx.marshalling.{Marshaller, BasicMarshallers}
import spray.util._
import spray.http._
import HttpHeaders._


trait FileAndResourceDirectives {
  import BasicDirectives._
  import ExecutionDirectives._
  import MethodDirectives._
  import RespondWithDirectives._
  import RouteDirectives._
  import MiscDirectives._
  import FileAndResourceDirectives._

  /**
   * Completes GET requests with the content of the given file. The actual I/O operation is
   * running detached in the context of a newly spawned actor, so it doesn't block the current thread (but potentially
   * some other thread !). If the file cannot be found or read the request is rejected.
   */
  def getFromFile(fileName: String)(implicit settings: RoutingSettings, resolver: ContentTypeResolver,
                                    refFactory: ActorRefFactory): Route =
    getFromFile(new File(fileName))

  /**
   * Completes GET requests with the content of the given file. The actual I/O operation is
   * running detached in the context of a newly spawned actor, so it doesn't block the current thread (but potentially
   * some other thread !). If the file cannot be found or read the request is rejected.
   */
  def getFromFile(file: File)(implicit settings: RoutingSettings, resolver: ContentTypeResolver,
                              refFactory: ActorRefFactory): Route =
    get {
      detachTo(singleRequestServiceActor) {
        respondWithLastModifiedHeader(file.lastModified) {
          if (file.isFile && file.canRead) {
            implicit val bufferMarshaller = BasicMarshallers.byteArrayMarshaller(resolver(file.getName))
            if (0 < settings.fileChunkingThresholdSize && settings.fileChunkingThresholdSize <= file.length)
              complete(file.toByteArrayStream(settings.fileChunkingChunkSize.toInt))
            else complete(FileUtils.readAllBytes(file))
          } else reject
        }
      }
    }

  /**
   * Adds a Last-Modified header to all HttpResponses from its inner Route.
   */
  def respondWithLastModifiedHeader(timestamp: Long): Directive0 =
    respondWithHeader(`Last-Modified`(DateTime(math.min(timestamp, System.currentTimeMillis))))

  /**
   * Completes GET requests with the content of the given resource. The actual I/O operation is
   * running detached in the context of a newly spawned actor, so it doesn't block the current thread (but potentially
   * some other thread !).
   * If the file cannot be found or read the Route rejects the request.
   */
  def getFromResource(resourceName: String)
                     (implicit resolver: ContentTypeResolver, refFactory: ActorRefFactory): Route = {
    def openConnection: Option[URL] :: HNil => Directive[URLConnection :: HNil] = {
      case Some(url) :: HNil => provide(url.openConnection())
      case _ => reject
    }
    if (!resourceName.endsWith("/")) {
      def resource = actorSystem(refFactory).dynamicAccess.classLoader.getResource(resourceName)
      (get & detachTo(singleRequestServiceActor) & provide(Option(resource)))
        .hflatMap(openConnection) { urlConn =>
          implicit val bufferMarshaller = BasicMarshallers.byteArrayMarshaller(resolver(resourceName))
          respondWithLastModifiedHeader(urlConn.getLastModified) {
            complete(FileUtils.readAllBytes(urlConn.getInputStream))
          }
        }
    } else reject // don't serve the content of resource "directories"
  }

  /**
   * Completes GET requests with the content of a file underneath the given directory.
   * The unmatchedPath of the [[spray.RequestContext]] is first transformed by the given pathRewriter function before
   * being appended to the given directoryName to build the final fileName.
   * The actual I/O operation is running detached in the context of a newly spawned actor, so it doesn't block the
   * current thread. If the file cannot be read the Route rejects the request.
   */
  def getFromDirectory(directoryName: String)(implicit settings: RoutingSettings, resolver: ContentTypeResolver,
                                              refFactory: ActorRefFactory): Route = {
    val base = withTrailingSlash(directoryName)
    unmatchedPath { path =>
      getFromFile(base + stripLeadingSlash(path))
    }
  }

  /**
   * Completes GET requests with a unified listing of the contents of all given directories.
   * The actual rendering of the directory contents is performed by the in-scope `Marshaller[DirectoryListing]`.
   */
  def listDirectoryContents(directories: String*)
                           (implicit renderer: Marshaller[DirectoryListing], refFactory: ActorRefFactory): Route = {
    get {
      detachTo(singleRequestServiceActor) {
        unmatchedPath { path =>
          val pathString = path.toString
          val dirs = directories.map(new File(_, pathString)).filter(dir => dir.isDirectory && dir.canRead)
          if (dirs.isEmpty) reject
          else complete(DirectoryListing(withTrailingSlash(pathString), dirs.flatMap(_.listFiles)))
        }
      }
    }
  }

  /**
   * Same as `getFromBrowseableDirectories` with only one directory.
   */
  def getFromBrowseableDirectory(directory: String)
                                (implicit renderer: Marshaller[DirectoryListing], settings: RoutingSettings,
                                 resolver: ContentTypeResolver, refFactory: ActorRefFactory): Route =
    getFromBrowseableDirectories(directory)

  /**
   * Serves the content of the given directories as a file system browser, i.e. files are sent and directories
   * served as browsable listings.
   */
  def getFromBrowseableDirectories(directories: String*)
                                  (implicit renderer: Marshaller[DirectoryListing], settings: RoutingSettings,
                                   resolver: ContentTypeResolver, refFactory: ActorRefFactory): Route = {
    import RouteConcatenation._
    directories.map(getFromDirectory(_)).reduceLeft(_ ~ _) ~ listDirectoryContents(directories: _*)
  }

  /**
   * Same as "getFromDirectory" except that the file is not fetched from the file system but rather from a
   * "resource directory".
   */
  def getFromResourceDirectory(directoryName: String)
                              (implicit resolver: ContentTypeResolver, refFactory: ActorRefFactory): Route = {
    val base = if (directoryName.isEmpty) "" else withTrailingSlash(directoryName)
    unmatchedPath { path =>
      getFromResource(base + stripLeadingSlash(path).toString)
    }
  }
}

object FileAndResourceDirectives extends FileAndResourceDirectives {
  def stripLeadingSlash(path: Uri.Path) = if (path.startsWithSlash) path.tail else path
  def withTrailingSlash(path: String) = if (path endsWith "/") path else path + '/'
}


trait ContentTypeResolver {
  def apply(fileName: String): ContentType
}

object ContentTypeResolver {

  /**
   * The default way of resolving a filename to a ContentType is by looking up the file extension in the
   * registry of all defined media-types. By default all non-binary file content is assumed to be UTF-8 encoded.
   */
  implicit val Default = withDefaultCharset(HttpCharsets.`UTF-8`)

  def withDefaultCharset(charset: HttpCharset): ContentTypeResolver =
    new ContentTypeResolver {
      def apply(fileName: String) = {
        val mediaType =
          MediaTypes.forExtension(
            fileName.lastIndexOf('.') match {
              case -1 => ""
              case x => fileName.substring(x + 1)
            }
          ).getOrElse(MediaTypes.`application/octet-stream`)
        mediaType match {
          case x if !x.binary => ContentType(x, charset)
          case x => ContentType(x)
        }
      }
    }
}

case class DirectoryListing(path: String, files: Seq[File])

object DirectoryListing {

  private val html =
    """<html>
      |<head><title>Index of $</title></head>
      |<body>
      |<h1>Index of $</h1>
      |<hr>
      |<pre>
      |$</pre>
      |<hr>$
      |<div style="width:100%;text-align:right;color:gray">
      |<small>rendered by <a href="http://spray.io">spray</a> on $</small>
      |</div>$
      |</body>
      |</html>
      |""".stripMargin split '$'

  implicit def DefaultMarshaller(implicit settings: RoutingSettings): Marshaller[DirectoryListing] =
    Marshaller.delegate[DirectoryListing, String](MediaTypes.`text/html`) { listing =>
      val DirectoryListing(path, files) = listing
      val filesAndNames = files.map(file => file -> file.getName).sortBy(_._2)
      val deduped = filesAndNames.zipWithIndex.flatMap { case (fan@(file, name), ix) =>
        if (ix == 0 || filesAndNames(ix - 1)._2 != name) Some(fan) else None
      }
      val (directoryFilesAndNames, fileFilesAndNames) = deduped.partition(_._1.isDirectory)
      def maxNameLength(seq: Seq[(File, String)]) = if (seq.isEmpty) 0 else seq.map(_._2.length).max
      val maxNameLen = math.max(maxNameLength(directoryFilesAndNames) + 1, maxNameLength(fileFilesAndNames))
      val sb = new java.lang.StringBuilder
      sb.append(html(0)).append(path).append(html(1)).append(path).append(html(2))
      if (path != "/") {
        val secondToLastSlash = path.lastIndexOf('/', path.lastIndexOf('/', path.length-1)-1)
        sb.append("<a href=\"%s/\">../</a>\n" format path.substring(0, secondToLastSlash))
      }
      def lastModified(file: File) = DateTime(file.lastModified).toIsoLikeDateTimeString
      def start(name: String) =
        sb.append("<a href=\"").append(path + name).append("\">").append(name).append("</a>")
          .append(" " * (maxNameLen - name.length))
      def renderDirectory(file: File, name: String) =
        start(name + '/').append("        ").append(lastModified(file)).append('\n')
      def renderFile(file: File, name: String) = {
        val size = Utils.humanReadableByteCount(file.length, si = true)
        start(name).append("        ").append(lastModified(file))
        sb.append("                ".substring(size.length)).append(size).append('\n')
      }
      for ((file, name) <- directoryFilesAndNames) renderDirectory(file, name)
      for ((file, name) <- fileFilesAndNames) renderFile(file, name)
      if (path == "/" && files.isEmpty) sb.append("(no files)\n")
      sb.append(html(3))
      if (settings.renderVanityFooter) sb.append(html(4)).append(DateTime.now.toIsoLikeDateTimeString).append(html(5))
      sb.append(html(6)).toString
    }
}