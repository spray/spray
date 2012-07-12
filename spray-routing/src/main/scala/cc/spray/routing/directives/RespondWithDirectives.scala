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
  this: BasicDirectives =>

  /**
   * Returns a Route that sets the given response status on all not-rejected responses of its inner Route.
   */
  def respondWithStatus(responseStatus: StatusCode) = transformHttpResponse {
    _.copy(status = responseStatus)
  }

  /**
   * Returns a Route that adds the given response header to all not-rejected responses of its inner Route.
   */
  def respondWithHeader(responseHeader: HttpHeader) = transformHttpResponse { response =>
    response.copy(headers = responseHeader :: response.headers)
  }

  /**
   * Returns a Route that adds the given response headers to all not-rejected responses of its inner Route.
   */
  def respondWithHeaders(responseHeaders: HttpHeader*) = {
    val headers = responseHeaders.toList
    transformHttpResponse { response =>
      response.copy(headers = headers ::: response.headers)
    }
  }

  /**
   * Returns a Route that sets the Content-Type of non-empty, non-rejected responses of its inner Route to the given
   * ContentType.
   */
  def respondWithContentType(contentType: ContentType) = transformHttpResponse { response =>
    response.copy(entity = response.entity.map((ct, buffer) => (contentType, buffer)))
  }

  /**
   * Returns a Route that sets the media-type of non-empty, non-rejected responses of its inner Route to the given
   * one.
   */
  def respondWithMediaType(mediaType: MediaType) = transformHttpResponse { response =>
    response.copy(entity = response.entity.map((ct, buffer) => (ct.withMediaType(mediaType), buffer)))
  }
}

object RespondWithDirectives extends RespondWithDirectives with BasicDirectives