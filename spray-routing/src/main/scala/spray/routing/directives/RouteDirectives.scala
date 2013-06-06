/*
 * Copyright (C) 2011-2013 spray.io
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
package directives

import akka.dispatch.{ ExecutionContext, Future }
import spray.httpx.marshalling.Marshaller
import spray.http._
import StatusCodes._

trait RouteDirectives {

  /**
   * Rejects the request with an empty set of rejections.
   */
  val reject: StandardRoute = new StandardRoute {
    def apply(ctx: RequestContext) { ctx.reject() }
  }

  /**
   * Rejects the request with the given rejections.
   */
  def reject(rejections: Rejection*): StandardRoute = new StandardRoute {
    def apply(ctx: RequestContext) { ctx.reject(rejections: _*) }
  }

  /**
   * Completes the request with redirection response of the given type to the given URI.
   * The default redirectionType is a temporary `301 Moved Permanently`.
   */
  def redirect(uri: String, redirectionType: Redirection = MovedPermanently): StandardRoute = new StandardRoute {
    def apply(ctx: RequestContext) { ctx.redirect(uri, redirectionType) }
  }

  /**
   * Completes the request using the given arguments.
   */
  def complete: (⇒ CompletionMagnet) ⇒ StandardRoute = magnet ⇒ new StandardRoute {
    def apply(ctx: RequestContext) {
      magnet.route(ctx)
    }
  }

  /**
   * Bubbles the given error up the response chain, where it is dealt with by the closest `handleExceptions`
   * directive and its ExceptionHandler.
   */
  def failWith(error: Throwable): StandardRoute = new StandardRoute {
    def apply(ctx: RequestContext) { ctx.failWith(error) }
  }

}

object RouteDirectives extends RouteDirectives

trait CompletionMagnet {
  def route: StandardRoute
}

object CompletionMagnet {
  implicit def fromObject[T: Marshaller](obj: T) =
    new CompletionMagnet {
      def route: StandardRoute = new CompletionRoute(OK, Nil, obj)
    }
  implicit def fromStatusObject[T: Marshaller](tuple: (StatusCode, T)) =
    new CompletionMagnet {
      def route: StandardRoute = new CompletionRoute(tuple._1, Nil, tuple._2)
    }
  implicit def fromStatusHeadersObject[T: Marshaller](tuple: (StatusCode, List[HttpHeader], T)) =
    new CompletionMagnet {
      def route: StandardRoute = new CompletionRoute(tuple._1, tuple._2, tuple._3)
    }
  implicit def fromHttpResponse(response: HttpResponse) =
    new CompletionMagnet {
      def route = new StandardRoute {
        def apply(ctx: RequestContext) { ctx.complete(response) }
      }
    }
  implicit def fromStatus(status: StatusCode) =
    new CompletionMagnet {
      def route = new StandardRoute {
        def apply(ctx: RequestContext) { ctx.complete(status) }
      }
    }
  implicit def fromHttpResponseFuture(future: Future[HttpResponse])(implicit ec: ExecutionContext) =
    new CompletionMagnet {
      def route = new StandardRoute {
        def apply(ctx: RequestContext) { ctx.complete(future) }
      }
    }
  implicit def fromStatusCodeFuture(future: Future[StatusCode])(implicit ec: ExecutionContext): CompletionMagnet =
    future.map(status ⇒ HttpResponse(status, entity = status.defaultMessage))

  private class CompletionRoute[T: Marshaller](status: StatusCode, headers: List[HttpHeader], obj: T)
      extends StandardRoute {
    def apply(ctx: RequestContext) { ctx.complete(status, headers, obj) }
  }
}
