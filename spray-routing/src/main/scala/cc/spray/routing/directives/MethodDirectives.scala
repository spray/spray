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

import cc.spray.http.HttpMethod
import cc.spray.http.HttpMethods._


trait MethodDirectives {
  this: BasicDirectives =>

  /**
   * A route filter that rejects all non-DELETE requests.
   */
  val delete = method(DELETE)

  /**
   * A route filter that rejects all non-GET requests.
   */
  val get = method(GET)

  /**
   * A route filter that rejects all non-PATCH requests.
   */
  val patch = method(PATCH)

  /**
   * A route filter that rejects all non-POST requests.
   */
  val post = method(POST)

  /**
   * A route filter that rejects all non-PUT requests.
   */
  val put = method(PUT)


  /**
   * Returns a route filter that rejects all requests whose HTTP method does not match the given one.
   */
  def method(m: HttpMethod) = filter { ctx =>
    if (ctx.request.method == m) Pass.Empty else Reject(MethodRejection(m))
  }

}

object MethodDirectives extends MethodDirectives with BasicDirectives