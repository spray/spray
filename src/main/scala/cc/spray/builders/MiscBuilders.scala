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
  
  def requestTransformedBy(f: HttpRequest => HttpRequest)(route: Route): Route = { ctx =>
    route(ctx.withRequestTransformed(f))
  }
  
  def responseTransformedBy(f: HttpResponse => HttpResponse)(route: Route): Route = { ctx =>
    route(ctx.withHttpResponseTransformed(f))
  }
  
  def routingResultTransformedBy(f: RoutingResult => RoutingResult)(route: Route): Route = { ctx =>
    route(ctx.withRoutingResultTransformed(f))
  }
  
  def respondsWithStatus(responseStatus: HttpStatusCode) = responseTransformedBy { response =>
    response.copy(status = responseStatus)
  } _
  
  def respondsWithHeader(responseHeader: HttpHeader) = responseTransformedBy { response =>
    response.copy(headers = responseHeader :: response.headers)
  } _
  
  // uncachable
  def hardFail(failure: HttpFailure, reason: String = ""): Nothing = throw new HttpException(failure, reason)
  
  implicit def pimpRouteWithConcatenation(route: Route): { def ~ (other: Route): Route } = new {
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