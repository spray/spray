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

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import cc.spray.caching._
import cc.spray.http._
import CacheDirectives._
import HttpHeaders._

// not mixed into cc.spray.routing.Directives due to its dependency on com.googlecode.concurrentlinkedhashmap
trait CachingDirectives {
  import BasicDirectives._

  def system: ActorSystem
  def log: LoggingAdapter

  /**
   * Wraps its inner Route with caching support using the given [[cc.spray.caching.Cache]] implementation and
   * keyer function.
   */
  def cacheResults(cache: Cache[Either[Seq[Rejection], HttpResponse]],
                   keyer: CacheKeyer = CacheKeyers.UriGetCacheKeyer) =
    cachingProhibited | alwaysCacheResults(cache, keyer)

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
   * Wraps its inner Route with caching support using the given [[cc.spray.caching.Cache]] implementation and
   * keyer function. Note that routes producing streaming responses cannot be wrapped with this directive.
   * Route responses other than HttpResponse or Rejections trigger a "500 Internal Server Error" response.
   */
  def alwaysCacheResults(cache: Cache[Either[Seq[Rejection], HttpResponse]],
                         keyer: CacheKeyer = CacheKeyers.UriGetCacheKeyer): Directive0 =
    mapInnerRoute { route => ctx =>
      keyer(ctx) match {
        case Some(key) =>
          implicit val executionContext = system
          cache(key) { promise =>
            route {
              ctx.withRouteResponseHandling {
                case response: HttpResponse => promise.success(Right(response))
                case Reject(rejections) => promise.success(Left(rejections))
                case x =>
                  log.error("Route responses other than HttpResponse or Rejections cannot be cached (received: {})", x)
                  promise.failure(HttpException(500))
              }
            }
          } onComplete {
            case Right(Right(response)) => ctx.complete(response)
            case Right(Left(rejections)) => ctx.reject(rejections: _*)
            case Left(error) => ctx.fail(error)
          }

        case _ => route(ctx)
      }
    }

  /**
   * Wraps its inner Route with caching support using a default [[cc.spray.caching.LruCache]] instance
   * (max-entries = 500, initialCapacity = 16, time-to-idle: infinite) and the `CacheKeyers.UriGetCacheKeyer` which
   * only caches GET requests and uses the request URI as cache key.
   */
  lazy val cache = cacheResults(LruCache())
}


object CacheKeyers {

  case class FilteredCacheKeyer(filter: CacheKeyFilter, inner: CacheKeyer) extends CacheKeyer {
    def apply(ctx: RequestContext) = if (filter(ctx)) inner(ctx) else None
    def & (f: CacheKeyFilter) = FilteredCacheKeyer(c => filter(c) && f(c), inner)
  }

  val GetFilter: CacheKeyFilter = { _.request.method == HttpMethods.GET }
  val UriKeyer: CacheKeyer = { c => Some(c.request.uri) }
  val UriGetCacheKeyer = FilteredCacheKeyer(GetFilter, UriKeyer)
}