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

import cc.spray.http._


trait RespondWithDirectives {
  import BasicDirectives._

  /**
   * Returns a Route that sets the given response status on all HTTP responses of its inner Route.
   */
  def respondWithStatus(responseStatus: StatusCode): Directive0 =
    mapHttpResponse(_.copy(status = responseStatus))

  /**
   * Returns a Route that adds the given response header to all HTTP responses of its inner Route.
   */
  def respondWithHeader(responseHeader: HttpHeader): Directive0 =
    mapHttpResponseHeaders(responseHeader :: _)

  /**
   * Returns a Route that adds the given response header to all HTTP responses of its inner Route,
   * if the response from the inner Route doesn't already contain a header with the same name.
   */
  def respondWithSingletonHeader(responseHeader: HttpHeader): Directive0 =
    mapHttpResponseHeaders { headers =>
      if (headers.exists(_.name == responseHeader.name)) headers
      else responseHeader :: headers
    }

  /**
   * Returns a Route that adds the given response headers to all HTTP responses of its inner Route.
   */
  def respondWithHeaders(responseHeaders: HttpHeader*): Directive0 = {
    val headers = responseHeaders.toList
    mapHttpResponseHeaders(headers ::: _)
  }
}

object RespondWithDirectives extends RespondWithDirectives