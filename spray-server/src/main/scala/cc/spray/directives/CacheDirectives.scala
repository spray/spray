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

import cache._
import http._
import akka.util.Duration

private[spray] trait CacheDirectives {
  this: BasicDirectives =>

  val DefaultCacheName: String = "routes"
  val DefaultCacheBackend: CacheBackend = LRUCache

  def cache(ttl: Option[Duration] = None,
            policy: CachePolicy = CachePolicies.DefaultPolicy,
            name: String = DefaultCacheName,
            backend: CacheBackend = DefaultCacheBackend) = {
    cacheIn(backend(name), ttl, policy)
  }
  
  def cacheIn(basecache: Cache[Any],
              ttl: Option[Duration] = None,
              policy: CachePolicy = CachePolicies.DefaultPolicy) = {
    val cache = new TypedCache[HttpResponse](basecache)
    transform { route => ctx =>
      policy(ctx) match {
        case Some(key) => cache.get(key) match {
          case Some(response) => ctx.complete(response)
          case None => route {
            ctx.withHttpResponseTransformed { response =>
              cache.set(key, response, ttl)
              response
            }
          }
        }
        case _ => route(ctx)
      }
    }
  }
}

object CachePolicies {
  case class FilteredCachePolicy(filter: RequestContext => Boolean, inner: CachePolicy) extends CachePolicy {
    def apply(ctx: RequestContext) = if (filter(ctx)) inner(ctx) else None
    def & (f: RequestContext => Boolean) = FilteredCachePolicy(c => filter(c) && f(c), inner)
  }

  val GetFilter: RequestContext => Boolean = { _.request.method == HttpMethods.GET }
  val OnUriPolicy: CachePolicy = { c => Some(c.request.uri) }
  val DefaultPolicy = FilteredCachePolicy(GetFilter, OnUriPolicy)
}