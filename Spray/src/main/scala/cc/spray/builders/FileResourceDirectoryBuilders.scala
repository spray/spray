package cc.spray
package builders

import http._
import akka.actor.Actor
import org.parboiled.common.FileUtils
import java.io.File
import HttpStatusCodes._

private[spray] trait FileResourceDirectoryBuilders {
  this: BasicBuilders =>
  
  def getFromFile(fileName: String, charset: Option[Charset] = None)
                 (implicit detachedActorFactory: Route => Actor, resolver: ContentTypeResolver): Route = {
    detached {
      get {
        _.respond {
          Option(FileUtils.readAllBytes(fileName)).map { buffer =>
            HttpResponse(content = HttpContent(resolver(new File(fileName), charset), buffer))
          }.getOrElse(HttpResponse(NotFound))
        } 
      }
    }
  }
  
  def getFromResource(resourceName: String, charset: Option[Charset] = None)
                     (implicit detachedActorFactory: Route => Actor, resolver: ContentTypeResolver): Route = {
    detached {
      get {
        _.respond {
          Option(FileUtils.readAllBytesFromResource(resourceName)).map { buffer =>
            HttpResponse(content = HttpContent(resolver(new File(resourceName), charset), buffer))
          }.getOrElse(HttpResponse(NotFound))
        } 
      }
    }
  }
  
  def getFromDirectory(directoryName: String, charset: Option[Charset] = None)
                      (implicit detachedActorFactory: Route => Actor, resolver: ContentTypeResolver): Route = { ctx =>
    val subPath = if (File.pathSeparatorChar == '/') ctx.unmatchedPath
                  else ctx.unmatchedPath.replace('/', File.pathSeparatorChar) 
    getFromFile(directoryName + subPath, charset).apply(ctx)
  }
  
  def getFromResourceDirectory(directoryName: String, charset: Option[Charset] = None)
                              (implicit detachedActorFactory: Route => Actor,
                               resolver: ContentTypeResolver): Route = { ctx =>
    getFromResource(directoryName + ctx.unmatchedPath, charset).apply(ctx)
  }
  
  // implicits
  
  implicit def defaultContentTypeResolver(file: File, charset: Option[Charset]): ContentType = {
    val mimeType = MimeTypes.forExtension(file.extension).getOrElse(MimeTypes.`application/octet-stream`)
    charset match {
      case Some(cs) => ContentType(mimeType, cs)
      case None => ContentType(mimeType)
    }
  }
  
}