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

package spray.site

import akka.actor.Actor
import akka.event.Logging._
import shapeless._
import spray.routing.directives.{DirectoryListing, LogEntry}
import spray.httpx.encoding.Gzip
import spray.httpx.marshalling.Marshaller
import spray.httpx.TwirlSupport._
import spray.http._
import spray.routing._
import html._
import StatusCodes._


class SiteServiceActor extends Actor with HttpServiceActor {

  def receive = runRoute {
    dynamicIf(SiteSettings.DevMode) { // for proper support of twirl + sbt-revolver during development
      (get & encodeResponse(Gzip)) {
        (host("repo.spray.io", "repo.spray.cc")) {
          logRequestResponse(showRepoResponses("repo") _) {
            getFromBrowseableDirectories(SiteSettings.RepoDirs: _*) ~
            complete(NotFound)
          }
        } ~
        host("nightlies.spray.io") {
          logRequestResponse(showRepoResponses("nightlies") _) {
            getFromBrowseableDirectories(SiteSettings.NightliesDir) ~
            complete(NotFound)
          }
        } ~
        host("spray.io", "localhost", "127.0.0.1") {
          path("favicon.ico") {
            complete(NotFound) // fail early in order to prevent error response logging
          } ~
          logRequestResponse(showErrorResponses _) {
            getFromResourceDirectory("theme") ~
            pathPrefix("_images") {
              getFromResourceDirectory("sphinx/json/_images")
            } ~
            logRequest(showRequest _) {
              path("") {
                complete(page(home()))
              } ~
              pathTest(".*[^/]$".r) { _ => // if the path doesn't end with a slash
                unmatchedPath { ump =>
                  redirect(ump + "/")      // we redirect
                }
              } ~
              path("home") {
                redirect("/")
              } ~
              path("index") {
                complete(page(index()))
              } ~
              pathTest("blog") {
                path("blog") {
                  complete(page(sphinxBlogIndex(RootNode.blogRoot.children), RootNode.blogRoot))
                } ~
                path("blog/category" / PathElement) { tag =>
                  RootNode.childrenWithTag(tag) match {
                    case Nil => complete(NotFound, page(error404()))
                    case posts => complete(page(sphinxBlogIndex(posts, tag), RootNode.blogRoot))
                  }
                } ~
                sphinxNode { node =>
                  complete(page(sphinxBlogPost(node), node))
                }
              } ~
              sphinxNode { node =>
                complete(page(sphinxDoc(node), node))
              }
            }
          }
        } ~
        unmatchedPath { ump =>
          redirect("http://spray.io" + ump)
        }
      }
    }
  }

  val sphinxNode = path(Rest).map(RootNode.find).flatMap[ContentNode :: HNil] {
    case None => complete(NotFound, page(error404()))
    case Some(node) => provide(node)
  }

  def showRequest(request: HttpRequest) = LogEntry(request.uri, InfoLevel)

  def showErrorResponses(request: HttpRequest): Any => Option[LogEntry] = {
    case HttpResponse(OK, _, _, _) => None
    case HttpResponse(NotFound, _, _, _) => Some(LogEntry("404: " + request.uri, WarningLevel))
    case response => Some(
      LogEntry("Non-200 response for\n  Request : " + request + "\n  Response: " + response, WarningLevel)
    )
  }

  def showRepoResponses(repo: String)(request: HttpRequest): HttpResponsePart => Option[LogEntry] = {
    case HttpResponse(OK, _, _, _) => Some(LogEntry(repo + " 200: " + request.uri, InfoLevel))
    case ChunkedResponseStart(HttpResponse(OK, _, _, _)) => Some(LogEntry(repo + " 200 (chunked): " + request.uri, InfoLevel))
    case HttpResponse(NotFound, _, _, _) => Some(LogEntry(repo + " 404: " + request.uri))
    case _ => None
  }

  implicit val ListingMarshaller: Marshaller[DirectoryListing] =
    Marshaller.delegate(MediaTypes.`text/html`) { (listing: DirectoryListing) =>
      listing.copy(
        files = listing.files.filterNot( file =>
          file.getName.startsWith(".") || file.getName.startsWith("archetype-catalog")
        )
      )
    } (DirectoryListing.DefaultMarshaller)

}