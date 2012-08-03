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
package directives

import akka.dispatch.Future
import cc.spray.routing.authentication._
import cc.spray.util.LoggingContext
import shapeless._


trait SecurityDirectives {
  import BasicDirectives._

  /**
   * Wraps its inner Route with authentication support.
   */
  def authenticate(am: AuthMagnet): Directive[am.Out] = am()

  /**
   * Applies the given authorization check to the request.
   * If the check fails the route is rejected with an [[cc.spray.AuthorizationFailedRejection]].
   */
  def authorize(check: => Boolean): Directive0 = authorize(_ => check)

  /**
   * Applies the given authorization check to the request.
   * If the check fails the route is rejected with an [[cc.spray.AuthorizationFailedRejection]].
   */
  def authorize(check: RequestContext => Boolean): Directive0 = filter { ctx =>
    if (check(ctx)) Pass.Empty else Reject(AuthorizationFailedRejection)
  }

}

trait AuthMagnet {
  type Out <: HList
  def apply(): Directive[Out]
}

object AuthMagnet {
  private def applyAuth[T](auth: Future[Authentication[T]], f: (T :: HNil) => Route, ctx: RequestContext)
                          (implicit eh: ExceptionHandler, log: LoggingContext) = {
    auth.onComplete {
      case Right(Right(user)) => f(user :: HNil)(ctx)
      case Right(Left(rejection)) => ctx.reject(rejection)
      case Left(error) if eh.isDefinedAt(error) => eh(error)(log)(ctx)
      case Left(error) => ctx.fail(error)
    }
  }

  implicit def fromFutureAuth[T](auth: Future[Authentication[T]])
                                (implicit eh: ExceptionHandler, log: LoggingContext) =
    new AuthMagnet {
      type Out = T :: HNil
      def apply() = new Directive[Out] {
        def happly(f: Out => Route) = ctx => applyAuth(auth, f, ctx)
      }
    }

  implicit def fromContextAuthenticator[T](auth: ContextAuthenticator[T])
                                          (implicit eh: ExceptionHandler, log: LoggingContext) =
    new AuthMagnet {
      type Out = T :: HNil
      def apply() = new Directive[Out] {
        def happly(f: Out => Route) = ctx => applyAuth(auth(ctx), f, ctx)
      }
    }
}