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

import http._
import collection.mutable.WeakHashMap

private[spray] trait CacheDirectives {

  /**
   * Returns a Route that caches responses returned by its inner Route using the given keyer function.
   * The default keyer caches GET requests with the request URI as caching key, to all other requests it is fully
   * transparent. The cache itself is implemented as a [[collection.mutable.WeakHashMap]].
   */
  def cache(route: Route)(implicit keyer: RequestContext => CacheKey): Route = new Route {
    private val cache = WeakHashMap.empty[Any, HttpResponse]
    
    def apply(ctx: RequestContext) {
      val key = keyer(ctx) 
      if (key eq DontCache) {
        route(ctx)
      } else {
        cache.get(key) match {
          case Some(response) => ctx.complete(response)
          case None => route {
            ctx.withHttpResponseTransformed { response =>
              cache.update(key, response)
              response
            }
          }
        }
      }
    }
  }
  
  // implicits  
  
  implicit def defaultCacheKeyer(ctx: RequestContext): CacheKey = {
    if (ctx.request.method == HttpMethods.GET) CacheOn(ctx.request.uri) else DontCache
  }
  
}

/**
 * The result of the implicit cache keyer function argument to the 'cache' directive. 
 */
sealed trait CacheKey

/**
 * When the cache keyer function returns an instance of this class this instance is used as the key into the cache.
 */
case class CacheOn(key: Any) extends CacheKey

/**
 * When the cache keyer function returns this object the request will not be cached.
 */
case object DontCache extends CacheKey