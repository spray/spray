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
package directives

import akka.actor.ActorRefFactory
import akka.dispatch.ExecutionContext
import akka.util.Duration
import spray.util._
import spray.caching._
import spray.http._
import CacheDirectives._
import HttpHeaders._
import HttpMethods._


// not mixed into spray.routing.Directives due to its dependency on com.googlecode.concurrentlinkedhashmap
trait CachingDirectives {
  import BasicDirectives._

  type RouteResponse = Either[Seq[Rejection], HttpResponse]

  /**
   * Wraps its inner Route with caching support using the given [[spray.caching.Cache]] implementation and
   * the in-scope keyer function.
   */
  def cache(csm: CacheSpecMagnet): Directive0 = cachingProhibited | alwaysCache(csm)

  /**
   * Rejects the request if it doesn't contain a `Cache-Control` header with either a `no-cache` or `max-age=0` setting.
   */
  def cachingProhibited: Directive0 = filter { ctx =>
    val noCachePresent = ctx.request.headers.exists {
      case x: `Cache-Control` => x.directives.exists {
        case `no-cache` => true
        case `max-age`(0) => true
        case _ => false
      }
      case _ => false
    }
    if (noCachePresent) Pass.Empty else Reject.Empty
  }

  /**
   * Wraps its inner Route with caching support using the given [[spray.caching.Cache]] implementation and
   * in-scope keyer function. Note that routes producing streaming responses cannot be wrapped with this directive.
   * Route responses other than HttpResponse or Rejections trigger a "500 Internal Server Error" response.
   */
  def alwaysCache(csm: CacheSpecMagnet): Directive0 = {
    import csm._
    mapInnerRoute { route => ctx =>
      liftedKeyer(ctx) match {
        case Some(key) =>
          responseCache(key) { promise =>
            route {
              ctx.withRouteResponseHandling {
                case response: HttpResponse => promise.success(Right(response))
                case Reject(rejections) => promise.success(Left(rejections))
                case x =>
                  log.error("Route responses other than HttpResponse or Rejections cannot be cached (received: {})", x)
                  promise.failure(RequestProcessingException(StatusCodes.InternalServerError))
              }
            }
          } onComplete {
            case Right(Right(response)) => ctx.complete(response)
            case Right(Left(rejections)) => ctx.reject(rejections: _*)
            case Left(error) => ctx.failWith(error)
          }

        case None => route(ctx)
      }
    }
  }

  def routeCache(maxCapacity: Int = 500, initialCapacity: Int = 16, timeToLive: Duration = Duration.Zero,
                 timeToIdle: Duration = Duration.Zero): Cache[RouteResponse] =
    LruCache(maxCapacity, initialCapacity, timeToLive, timeToIdle)
}

object CachingDirectives extends CachingDirectives


trait CacheSpecMagnet {
  def responseCache: Cache[CachingDirectives.RouteResponse]
  def liftedKeyer: RequestContext => Option[Any]
  def log: LoggingContext
  implicit def executionContext: ExecutionContext
}

object CacheSpecMagnet {
  implicit def apply(cache: Cache[CachingDirectives.RouteResponse])
                    (implicit keyer: CacheKeyer, factory: ActorRefFactory, lc: LoggingContext) =
    new CacheSpecMagnet {
      def responseCache = cache
      def liftedKeyer = keyer.lift
      def log = lc
      implicit def executionContext = factory.messageDispatcher
    }
}


trait CacheKeyer extends (PartialFunction[RequestContext, Any])

object CacheKeyer {
  implicit val Default: CacheKeyer = CacheKeyer {
    case RequestContext(HttpRequest(GET, uri, _, _, _), _, _) => uri
  }

  def apply(f: PartialFunction[RequestContext, Any]) = new CacheKeyer {
    def isDefinedAt(ctx: RequestContext) = f.isDefinedAt(ctx)
    def apply(ctx: RequestContext) = f(ctx)
  }
}