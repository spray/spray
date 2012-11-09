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

import scala.util.control.NonFatal
import scala.concurrent.ExecutionContext
import akka.actor._
import spray.util.LoggingContext
import spray.http._
import StatusCodes._


trait HttpService extends Directives {

  warmUp() // trigger the loading of most classes in spray-http

  /**
   * An ActorRefFactory needs to be supplied by the class mixing us in
   * (mostly either the service actor or the service test)
   */
  implicit def actorRefFactory: ActorRefFactory

  /**
   * Supplies an ExecutionContext (mainly for Future scheduling) from the actorRefFactory.
   */
  implicit def executionContext: ExecutionContext = actorRefFactory.dispatcher


  val log = LoggingContext.fromActorRefFactory

  /**
   * Supplies the actor behavior for executing the given route.
   *
   * Note that the route parameter is call-by-name to alleviate initialization order issues when
   * mixing into an Actor.
   */
  def runRoute(route: Route)(implicit eh: ExceptionHandler, rh: RejectionHandler, ac: ActorContext,
                             rs: RoutingSettings): Actor.Receive = {
    val sealedExceptionHandler = eh orElse ExceptionHandler.default
    val sealedRoute = sealRoute(route)(sealedExceptionHandler, rh)
    def contextFor(req: HttpRequest) = RequestContext(req, ac.sender, req.path).withDefaultSender(ac.self)

    {
      case request: HttpRequest =>
        try {
          request.parseQuery.parseHeaders match {
            case ("", parsedRequest) =>
              sealedRoute(contextFor(parsedRequest))
            case (errorMsg, parsedRequest) if rs.RelaxedHeaderParsing =>
              log.warning("Request {}: {}", request, errorMsg)
              sealedRoute(contextFor(parsedRequest))
            case (errorMsg, _) =>
              throw new IllegalRequestException(BadRequest, RequestErrorInfo(errorMsg))
          }
        } catch {
          case NonFatal(e) =>
            val errorRoute = sealedExceptionHandler(e)(log)
            errorRoute(contextFor(request))
        }

      case ctx: RequestContext => sealedRoute(ctx)

      case Timeout(request: HttpRequest) => runRoute(timeoutRoute)(eh, rh, ac, rs)(request)
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

  //# timeout-route
  def timeoutRoute: Route = complete(
    InternalServerError,
    "The server was not able to produce a timely response to your request."
  )
  //#
}

trait HttpServiceActor extends HttpService {
  this: Actor =>

  def actorRefFactory = context
}