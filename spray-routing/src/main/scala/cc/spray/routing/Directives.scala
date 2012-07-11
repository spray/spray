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

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import cc.spray.http._
import HttpMethods._
import StatusCodes._
import cc.spray.httpx.marshalling.Marshaller


trait Directives extends RouteConcatenation {
  def system: ActorSystem
  def log: LoggingAdapter

  def handleExceptions(implicit handler: ExceptionHandler) = Directive.wrapping { inner => ctx =>
    try inner(ctx)
    catch handler andThen (_(log)(ctx))
  }

  def handleRejections(implicit handler: RejectionHandler) = Directive.responseTransforming {
    case Rejected(rejections) => handler(rejections)
  }

  /**
   * A route filter that rejects all non-DELETE requests.
   */
  val delete = method(DELETE)

  /**
   * A route filter that rejects all non-GET requests.
   */
  val get = method(GET)

  /**
   * A route filter that rejects all non-PATCH requests.
   */
  val patch = method(PATCH)

  /**
   * A route filter that rejects all non-POST requests.
   */
  val post = method(POST)

  /**
   * A route filter that rejects all non-PUT requests.
   */
  val put = method(PUT)


  /**
   * Returns a route filter that rejects all requests whose HTTP method does not match the given one.
   */
  def method(m: HttpMethod): Directive0 = Directive.filtering { ctx =>
    if (ctx.request.method == m) Pass.Empty else Reject(MethodRejection(m))
  }

  /**
   * Completes the request with the given [[cc.spray.http.HttpResponse]].
   */
  def completeWith(response: => HttpResponse): Route =
    _.complete(response)

  /**
   * Completes the request with status "200 Ok" and the response content created by marshalling the given object using
   * the in-scope marshaller for the type.
   */
  def completeWith[T :Marshaller](value: => T): Route = _.complete(value)

  /**
   * Completes the request with redirection response of the given type to the given URI.
   * The default redirectionType is a temporary `302 Found`.
   */
  def redirect(uri: String, redirectionType: Redirection = StatusCodes.Found): Route =
    _.redirect(uri, redirectionType)

  /**
   * Rejects the request with the given rejections.
   */
  def reject(rejections: Rejection*): Route =
    _.reject(rejections: _*)

  /**
   * Returns a Directive which checks the given condition before passing on the [[cc.spray.routing.RequestContext]] to
   * its inner Route. If the condition fails the route is rejected with a [[cc.spray.routing.ValidationRejection]].
   */
  def validate(check: => Boolean, errorMsg: String) = Directive.filtering { _ =>
    if (check) Pass.Empty else Reject(ValidationRejection(errorMsg))
  }
}
