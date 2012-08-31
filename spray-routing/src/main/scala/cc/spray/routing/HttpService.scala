/*
 * Copyright (C) 2011-2012 spray.cc
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

package cc.spray.routing

import akka.util.NonFatal
import akka.actor._
import cc.spray.http.{HttpException, HttpRequest}
import cc.spray.util.LoggingContext


trait HttpService extends Directives {

  /**
   * An ActorRefFactory needs to be supplied by the class mixing us in
   * (mostly either the service actor or the service test)
   */
  implicit def actorRefFactory: ActorRefFactory

  /**
   * Normally you configure via the application.conf on the classpath,
   * but you can also override this member.
   */
  implicit val settings = RoutingSettings()

  val log = LoggingContext.fromActorRefFactory

  /**
   * Supplies the actor behavior for executing the given route.
   * The argument is a call-by-name parameter to work around issues with the initialization order
   * when mixing into an Actor. Even though call-by-name the method only evaluates the parameter once.
   */
  def runRoute(route: => Route)
              (implicit eh: ExceptionHandler, rh: RejectionHandler, ac: ActorContext): Actor.Receive = {
    // we don't use a lazy val for the 'sealedRoute' member here, since we can be sure to be running in an Actor
    // (we require an implicit ActorContext) and can therefore avoid the "lazy val"-synchronization
    var sr: Route = null
    def sealedRoute: Route = { if (sr == null) sr = sealRoute(route); sr }
    def contextFor(req: HttpRequest) = RequestContext(req, ac.sender, req.path).withDefaultSender(ac.self)

    {
      case request: HttpRequest =>
        try {
          request.parseQuery.parseHeaders match {
            case ("", parsedRequest) =>
              sealedRoute(contextFor(parsedRequest))
            case (errorMsg, parsedRequest) if settings.RelaxedHeaderParsing =>
              log.warning("Request {}: {}", request, errorMsg)
              sealedRoute(contextFor(parsedRequest))
            case (errorMsg, _) =>
              throw new HttpException(400, errorMsg)
          }
        } catch {
          case NonFatal(e) =>
            val handler = if (eh.isDefinedAt(e)) eh else ExceptionHandler.Default
            val errorRoute = handler(e)(log)
            errorRoute(contextFor(request))
        }
    }
  }

  /**
   * "Seals" a route by wrapping it with exception handling and rejection conversion.
   */
  def sealRoute(route: Route)(implicit eh: ExceptionHandler, rh: RejectionHandler): Route =
    (handleExceptions(eh) & handleRejections(sealRejectionHandler(rh)))(route)

  def sealRejectionHandler(rh: RejectionHandler): RejectionHandler =
    rh orElse RejectionHandler.Default orElse handleUnhandledRejections

  def handleUnhandledRejections: RejectionHandler.PF = {
    case x :: _ => sys.error("Unhandled rejection: " + x)
  }
}