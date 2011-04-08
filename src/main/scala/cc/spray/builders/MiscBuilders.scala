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

private[spray] trait MiscBuilders {
  this: FilterBuilders =>

  /**
   * Returns a Route which applies the given [[cc.spray.http.HttpRequest]] transformation function before passing on the
   *  [[cc.spray.RequestContext]] to its inner Route.
   */
  def requestTransformedBy(f: HttpRequest => HttpRequest) = transform(_.withRequestTransformed(f))

  /**
   * Returns a Route which applies the given [[cc.spray.http.HttpResponse]] transformation function to all not-rejected
   * responses of its inner Route.
   */
  def responseTransformedBy(f: HttpResponse => HttpResponse) = transform(_.withHttpResponseTransformed(f))
  
  /**
   * Returns a Route which applies the given transformation function to the RoutingResult of its inner Route.
   */
  def routingResultTransformedBy(f: RoutingResult => RoutingResult) = transform(_.withRoutingResultTransformed(f))
  
  /**
   * Returns a Route that sets the given response status on all not-rejected responses of its inner Route.
   */
  def respondsWithStatus(responseStatus: HttpStatusCode) = responseTransformedBy { response =>
    response.copy(status = responseStatus)
  }

  /**
   * Returns a Route that adds the given response headers to all not-rejected responses of its inner Route.
   */
  def respondsWithHeader(responseHeader: HttpHeader) = responseTransformedBy { response =>
    response.copy(headers = responseHeader :: response.headers)
  }
  
  /**
   * Stops the current Route processing by throwing an HttpException that will be caught by the enclosing Actor.
   * Failures produced in this way circumvent all response processing logic that might be present (for example they
   * cannot be cached with the 'cached' directive).
   */
  def hardFail(failure: HttpFailure, reason: String = ""): Nothing = throw new HttpException(failure, reason)
  
  implicit def pimpRouteWithConcatenation(route: Route): { def ~ (other: Route): Route } = new {

    /**
     * Returns a Route that chains two Routes. If the first Route rejects the request the second route is given a
     * chance to act upon the request.
     */
    def ~ (other: Route): Route = { ctx =>
      route {
        ctx.withResponder { 
          _ match {
            case x: Respond => ctx.responder(x) // first route succeeded
            case Reject(rejections1) => other {
              ctx.withResponder {
                _ match {
                  case x: Respond => ctx.responder(x) // second route succeeded
                  case Reject(rejections2) => ctx.reject(rejections1 ++ rejections2)  
                }
              }
            }  
          }
        }
      }
    }
  }
  
}