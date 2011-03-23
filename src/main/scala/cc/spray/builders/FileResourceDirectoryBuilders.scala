package cc.spray
package builders

import http._
import akka.actor.Actor
import org.parboiled.common.FileUtils
import java.io.File
import HttpStatusCodes._

private[spray] trait FileResourceDirectoryBuilders {
  this: SimpleFilterBuilders with DetachedBuilders=>
  
  def getFromFile(fileName: String, charset: Option[Charset] = None)
                 (implicit detachedActorFactory: Route => Actor, resolver: ContentTypeResolver): Route = {
    detached {
      get {
        _.complete {
          Option(FileUtils.readAllBytes(fileName)).map { buffer =>
            HttpResponse(content = Some(HttpContent(resolver(new File(fileName), charset), buffer)))
          }.getOrElse(HttpResponse(NotFound))
        } 
      }
    }
  }
  
  def getFromResource(resourceName: String, charset: Option[Charset] = None)
                     (implicit detachedActorFactory: Route => Actor, resolver: ContentTypeResolver): Route = {
    detached {
      get {
        _.complete {
          Option(FileUtils.readAllBytesFromResource(resourceName)).map { buffer =>
            HttpResponse(content = Some(HttpContent(resolver(new File(resourceName), charset), buffer)))
          }.getOrElse(HttpResponse(NotFound))
        } 
      }
    }
  }
  
  def getFromDirectory(directoryName: String, charset: Option[Charset] = None,
                       pathRewriter: String => String = identity)
                      (implicit detachedActorFactory: Route => Actor, resolver: ContentTypeResolver): Route = { ctx =>
    getFromFile(directoryName + "/" + pathRewriter(ctx.unmatchedPath), charset).apply(ctx.copy(unmatchedPath = ""))
  }
  
  def getFromResourceDirectory(directoryName: String, charset: Option[Charset] = None,
                               pathRewriter: String => String = identity)
                              (implicit detachedActorFactory: Route => Actor,
                               resolver: ContentTypeResolver): Route = { ctx =>
    val path = if (directoryName.isEmpty) "" else directoryName + "/"
    getFromResource(path + pathRewriter(ctx.unmatchedPath), charset).apply(ctx.copy(unmatchedPath = ""))
  }
  
  // implicits
  
  implicit def defaultContentTypeResolver(file: File, charset: Option[Charset]): ContentType = {
    val mimeType = MediaTypes.forExtension(file.extension).getOrElse(MediaTypes.`application/octet-stream`)
    charset match {
      case Some(cs) => ContentType(mimeType, cs)
      case None => ContentType(mimeType)
    }
  }
  
}