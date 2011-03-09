package cc.spray
package builders

import http._
import HttpMethods._
import akka.actor.Actor
import org.parboiled.common.FileUtils
import java.io.File

private[spray] trait FileResourceDirectoryBuilders {
  this: BasicBuilders =>
  
  def getFromFile(filename: String)(implicit detachedActorFactory: Route => Actor,
                                    mimeType4FileResolver: File => MimeType): Route = {
    getFromFile(new File(filename))
  }
  
  def getFromFile(file: File)(implicit detachedActorFactory: Route => Actor,
                              mimeType4FileResolver: File => MimeType): Route = {
    detached {
      produces(mimeType4FileResolver(file)) {
        get { ctx =>
          val content = FileUtils.readAllBytes(file)
          if (content != null) ctx.respond(content)
          else ctx.fail(HttpStatusCodes.NotFound, "File '" + file + "' not found")
        }
      }
    }
  }
  
  def getFromResource(resourceName: String)(implicit detachedActorFactory: Route => Actor,
                                            mimeType4FileResolver: File => MimeType): Route = {
    detached {
      produces(mimeType4FileResolver(new File(resourceName))) {
        get { ctx =>
          val content = FileUtils.readAllBytesFromResource(resourceName)
          if (content != null) ctx.respond(content)
          else ctx.fail(HttpStatusCodes.NotFound, "Resource '" + resourceName + "' not found")
        }
      }
    }
  }
  
  /*def getFromDirectory(directoryName: String)(implicit detachedActorFactory: Route => Actor,
                                              mimeType4FileResolver: File => MimeType): Route = {
    getFromDirectory(new File(directoryName))
  }
  
  def getFromDirectory(directory: File)(implicit detachedActorFactory: Route => Actor,
                                        mimeType4FileResolver: File => MimeType): Route = {
    detached {
      produces(mimeType4FileResolver(file)) {
        get { ctx =>
          val content = FileUtils.readAllBytes(file)
          if (content != null) ctx.respond(content)
          else ctx.fail(HttpStatusCodes.InternalServerError, "File '" + file + "' not found")
        }
      }
    }
  }*/
  
  // implicits
  
  implicit def defaultMimeType4FileResolver(file: File): MimeType = {
    MimeTypes.forExtension(file.extension).getOrElse(MimeTypes.`application/octet-stream`)
  }
  
}