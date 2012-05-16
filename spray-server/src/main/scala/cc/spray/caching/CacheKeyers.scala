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

package cc.spray
package caching

import http._

object CacheKeyers {
  type CacheKeyFilter = RequestContext => Boolean

  case class FilteredCacheKeyer(filter: CacheKeyFilter, inner: CacheKeyer) extends CacheKeyer {
    def apply(ctx: RequestContext) = if (filter(ctx)) inner(ctx) else None
    def & (f: CacheKeyFilter) = FilteredCacheKeyer(c => filter(c) && f(c), inner)
  }

  val GetFilter: CacheKeyFilter = { _.request.method == HttpMethods.GET }
  val UriKeyer: CacheKeyer = { c => Some(c.request.uri) }
  val UriGetCacheKeyer = FilteredCacheKeyer(GetFilter, UriKeyer)
}

