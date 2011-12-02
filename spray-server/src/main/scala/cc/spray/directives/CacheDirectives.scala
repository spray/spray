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

import caching._
import http.HttpResponse

private[spray] trait CacheDirectives {
  this: BasicDirectives =>

  /**
   * Wraps its inner Route with caching support using the given [[cc.spray.caching.Cache]] implementation and
   * keyer function.
   */
  def cacheResults(cache: Cache[Either[Set[Rejection], HttpResponse]],
                   keyer: CacheKeyer = CacheKeyers.UriGetCacheKeyer) = {
    transformRoute { route => ctx =>
      keyer(ctx) match {
        case Some(key) => {
          cache(key) { completableFuture =>
            route {
              ctx
                .withComplete(response => completableFuture.completeWithResult(Right(response)))
                .withReject(rejections => completableFuture.completeWithResult(Left(rejections)))
            }
          } onResult {
            case Right(response) => ctx.responder.complete(response)
            case Left(rejections) => ctx.responder.reject(rejections)
          }
        }
        case _ => route(ctx)
      }
    }
  }

  /**
   * Wraps its inner Route with caching support using a default [[cc.spray.caching.LruCache]] instance
   * (max-entries = 500, dropFraction = 20%, time-to-live: 5 minutes) and the {{UriGetCacheKeyer}} which
   * only caches GET requests and uses the request URI as cache key.
   */
  lazy val cache = cacheResults(LruCache())
}