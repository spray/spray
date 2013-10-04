/*
 * Copyright © 2011-2013 the spray project <http://spray.io>
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
import spray.httpx.marshalling.{ Marshaller, ToResponseMarshaller }
import spray.http._
import StatusCodes._

trait RouteDirectives {

  /**
   * Rejects the request with an empty set of rejections.
   */
  def reject: StandardRoute = RouteDirectives._reject

  /**
   * Rejects the request with the given rejections.
   */
  def reject(rejections: Rejection*): StandardRoute = new StandardRoute {
    def apply(ctx: RequestContext): Unit = ctx.reject(rejections: _*)
  }

  /**
   * Completes the request with redirection response of the given type to the given URI.
   */
  def redirect(uri: Uri, redirectionType: Redirection): StandardRoute = new StandardRoute {
    def apply(ctx: RequestContext): Unit = ctx.redirect(uri, redirectionType)
  }

  /**
   * Completes the request using the given arguments.
   */
  def complete: (⇒ CompletionMagnet) ⇒ StandardRoute = magnet ⇒ new StandardRoute {
    def apply(ctx: RequestContext): Unit = magnet.apply(ctx)
  }

  /**
   * Bubbles the given error up the response chain, where it is dealt with by the closest `handleExceptions`
   * directive and its ExceptionHandler.
   */
  def failWith(error: Throwable): StandardRoute = new StandardRoute {
    def apply(ctx: RequestContext): Unit = ctx.failWith(error)
  }
}

object RouteDirectives extends RouteDirectives {
  private val _reject: StandardRoute = new StandardRoute {
    def apply(ctx: RequestContext): Unit = ctx.reject()
  }
}

sealed abstract class CompletionMagnet extends Route

object CompletionMagnet {
  implicit def fromObject[T: ToResponseMarshaller](obj: T): CompletionMagnet =
    new CompletionRoute(obj)

  implicit def fromStatusObject[T: Marshaller](tuple: (StatusCode, T)): CompletionMagnet =
    fromStatusHeadersObject((tuple._1, Nil, tuple._2))

  implicit def fromStatusHeadersObject[T: Marshaller](tuple: (StatusCode, List[HttpHeader], T)): CompletionMagnet =
    new CompletionRoute(tuple._3)(ToResponseMarshaller.fromMarshaller(tuple._1, tuple._2))

  implicit def fromHttpResponse(response: HttpResponse): CompletionMagnet =
    new CompletionMagnet {
      def apply(ctx: RequestContext): Unit = ctx.complete(response)
    }
  implicit def fromStatus(status: StatusCode): CompletionMagnet =
    new CompletionMagnet {
      def apply(ctx: RequestContext): Unit = ctx.complete(status)
    }
  implicit def fromHttpResponseFuture(future: Future[HttpResponse])(implicit ec: ExecutionContext): CompletionMagnet =
    new CompletionMagnet {
      def apply(ctx: RequestContext): Unit = ctx.complete(future)
    }
  implicit def fromStatusCodeFuture(future: Future[StatusCode])(implicit ec: ExecutionContext): CompletionMagnet =
    future.map(status ⇒ HttpResponse(status, entity = status.defaultMessage))

  private class CompletionRoute[T: ToResponseMarshaller](obj: T) extends CompletionMagnet {
    def apply(ctx: RequestContext): Unit = ctx.complete(obj)
  }
}
