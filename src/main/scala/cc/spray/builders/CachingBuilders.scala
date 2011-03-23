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
package builders

import http._
import collection.mutable.WeakHashMap

private[spray] trait CachingBuilders {
  
  def cached(route: Route)(implicit keyer: RequestContext => CacheKey): Route = new Route {
    private val cache = WeakHashMap.empty[Any, HttpResponse]
    
    def apply(ctx: RequestContext) {
      val key = if (ctx.request.method == HttpMethods.GET) keyer(ctx) else DontCache
      if (key eq DontCache) {
        route(ctx)
      } else {
        cache.get(key) match {
          case Some(response) => ctx.responder(Respond(response))
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
  
  implicit def defaultCacheKeyer(ctx: RequestContext): CacheKey = CacheOn(ctx.request.uri)
  
}

sealed trait CacheKey
case class CacheOn(key: Any) extends CacheKey
case object DontCache extends CacheKey