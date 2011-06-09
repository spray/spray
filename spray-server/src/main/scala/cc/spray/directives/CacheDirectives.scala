/*
 * Copyright (C) 2011 Mathias Doenitz
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

package cc.spray
package directives

import cache.{CacheBackend, LRUCache, Cache, TypedCache}
import http._
import akka.util.Duration

private[spray] trait CacheDirectives {
  import policy.Police

  /**
   * Returns a Route that caches responses returned by its inner Route using the given keyer function.
   * The default keyer caches GET requests with the request URI as caching key, to all other requests it is fully
   * transparent. The cache itself is implemented with a [[cc.spray.cache.CacheBackend]].
   */

  def cache(route: Route): Route = {
    cache()(route)
  }
  def cache(
        ttl: Duration = defaultTimeToLive,
        on: Police = defaultCachePolice,
        in: String = defaultCacheName,
        backend: CacheBackend = defaultCacheBackend
      )(route: Route):Route = {
    cacheIn(backend(in), ttl, on)(route)
  }
  def cacheIn(
        basecache: Cache[Any],
        ttl: Duration = defaultTimeToLive,
        on: Police = defaultCachePolice
      )(route: Route): Route = {
    val cache = new TypedCache[HttpResponse](basecache)
    new CachedRoute(route, cache, on, ttl)
  }
  // defaults
  val defaultCacheName: String = "routes"
  val defaultCachePolice: Police = Police
  val defaultCacheBackend: CacheBackend = LRUCache
  val defaultTimeToLive: Duration = Duration.MinusInf
}

class CachedRoute(
  val route: Route,
  val cache: Cache[HttpResponse],
  val police: policy.Police,
  val timeToLive: Duration) extends Route {

  def apply(ctx: RequestContext) {
    police(ctx) match {
      case Some(key) => {
        val ttl = if (timeToLive == Duration.MinusInf) {
          cache.timeToLive
        } else timeToLive
        val response = cache(key, extractResponseOrReject(ctx), ttl)
        if (response != null) ctx.complete(response)
        // already rejected if null
      }
      case _ => route(ctx)
    }
  }
  protected def extractResponseOrReject(ctx: RequestContext) = {
    var result: HttpResponse = null
    route(ctx.withResponder(_ match {
      case Respond(r) => result = r
      case x: Reject => ctx.responder(x)
    }))
    result
  }
}

package object policy {
  type Police = RequestContext => Option[Any]
  type Filter = RequestContext => Boolean
  class FilteredPolice protected(val filter: Filter, val police: Police) extends Police {
    def apply(ctx: RequestContext) = if (filter(ctx)) police(ctx) else None
    def apply(f: Filter, p: Police) = new FilteredPolice(f, p)
    def apply(p: Police) = new FilteredPolice(filter, p)
    def &(f:Filter) = new FilteredPolice({ c => filter(c) && f(c) }, police)
  }
  val GetFilter: Filter = { _.request.method == HttpMethods.GET }
  val UriPolice: Police = { c => Some(c.request.uri) }
  object Police extends FilteredPolice(GetFilter, UriPolice)
}
