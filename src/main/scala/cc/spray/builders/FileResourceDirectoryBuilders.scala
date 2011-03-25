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
                      (implicit detachedActorFactory: Route => Actor, resolver: ContentTypeResolver): Route = {
    val base = if (directoryName.endsWith("/")) directoryName else directoryName + "/";
    { ctx => getFromFile(base + pathRewriter(ctx.unmatchedPath), charset).apply(ctx.copy(unmatchedPath = "")) }
  }
  
  def getFromResourceDirectory(directoryName: String, charset: Option[Charset] = None,
                               pathRewriter: String => String = identity)
                              (implicit detachedActorFactory: Route => Actor,
                               resolver: ContentTypeResolver): Route = {
    val base = if (directoryName.isEmpty) "" else directoryName + "/";
    { ctx => getFromResource(base + pathRewriter(ctx.unmatchedPath), charset).apply(ctx.copy(unmatchedPath = "")) }
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