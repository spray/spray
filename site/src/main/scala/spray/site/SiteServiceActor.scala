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
import akka.event.Logging
import spray.routing.directives.LogEntry
import spray.httpx.encoding.Gzip
import spray.httpx.TwirlSupport._
import spray.http._
import spray.routing._
import html._
import StatusCodes._


class SiteServiceActor extends Actor with HttpServiceActor {

  def receive = runRoute {
    dynamicIf(SiteSettings.DevMode) { // for proper support of twirl + sbt-revolver during development
      (encodeResponse(Gzip) & logRequestResponse(showErrorResponses _) & get) {
        (host("repo.spray.io") | host("repo.spray.cc")) {
          getFromBrowseableDirectories(SiteSettings.RepoDirs: _*)
        } ~
        getFromResourceDirectory {
          "theme"
        } ~
        pathPrefix("_images") {
          getFromResourceDirectory("sphinx/json/_images")
        } ~
        logRequest(showRequest _) {
          path("") {
            complete(page(home()))
          } ~
          path("home") {
            redirect("/", MovedPermanently)
          } ~
          path("index") {
            complete(page(index()))
          } ~
          path(Rest) { docPath =>
            rejectEmptyResponse {
              complete(render(docPath))
            }
          } ~
          complete(NotFound, page(error404())) // fallback response is 404
        }
      }
    }
  }

  def render(docPath: String) =
    RootNode.find(docPath) map { node =>
      SphinxDoc.load(node.uri).orElse(SphinxDoc.load(node.uri + "index")) match {
        case Some(SphinxDoc(body)) => page(sphinxDoc(node, body), node)
        case None => throw new RuntimeException("SphinxDoc for uri '%s' not found" format node.uri)
      }
    }

  def showRequest(request: HttpRequest) = LogEntry(request.uri, Logging.InfoLevel)

  def showErrorResponses(request: HttpRequest): Any => Option[LogEntry] = {
    case HttpResponse(OK, _, _, _) => None
    case HttpResponse(NotFound, _, _, _) =>
      Some(LogEntry("404 response for GET " + request.uri, Logging.WarningLevel))
    case response => Some(
      LogEntry("Non-200 response for\n  Request : " + request + "\n  Response: " + response, Logging.WarningLevel)
    )
  }

}