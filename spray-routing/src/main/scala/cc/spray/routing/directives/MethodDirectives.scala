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

import spray.http.HttpMethod
import spray.http.HttpMethods._


trait MethodDirectives {
  import BasicDirectives._
  import MiscDirectives._

  /**
   * A route filter that rejects all non-DELETE requests.
   */
  val delete = method(DELETE) // source-quote

  /**
   * A route filter that rejects all non-GET requests.
   */
  val get = method(GET) // source-quote

  /**
   * A route filter that rejects all non-PATCH requests.
   */
  val patch = method(PATCH) // source-quote

  /**
   * A route filter that rejects all non-POST requests.
   */
  val post = method(POST) // source-quote

  /**
   * A route filter that rejects all non-PUT requests.
   */
  val put = method(PUT) // source-quote

  /**
   * Rejects all requests whose HTTP method does not match the given one.
   */
  def method(m: HttpMethod): Directive0 = filter { ctx =>
    if (ctx.request.method == m) Pass.Empty else Reject(MethodRejection(m))
  } & cancelAllRejections(ofType[MethodRejection])
}

object MethodDirectives extends MethodDirectives