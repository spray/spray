package cc.spray
package builders

import http._
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
  
  def getFromDirectory(directoryName: String)(implicit detachedActorFactory: Route => Actor,
                                              mimeType4FileResolver: File => MimeType): Route = { ctx =>
    val subPath = if (File.pathSeparatorChar == '/') ctx.unmatchedPath
                  else ctx.unmatchedPath.replace('/', File.pathSeparatorChar) 
    getFromFile(directoryName + subPath).apply(ctx)
  }
  
  def getFromResourceDirectory(directoryName: String)(implicit detachedActorFactory: Route => Actor,
                                                      mimeType4FileResolver: File => MimeType): Route = { ctx =>
    getFromResource(directoryName + ctx.unmatchedPath).apply(ctx)
  }
  
  // implicits
  
  implicit def defaultMimeType4FileResolver(file: File): MimeType = {
    MimeTypes.forExtension(file.extension).getOrElse(MimeTypes.`application/octet-stream`)
  }
  
}