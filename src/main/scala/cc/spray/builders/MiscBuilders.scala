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
                  case Reject(rejections2) => ctx.responder(Reject(rejections1 ++ rejections2))  
                }
              }
            }  
          }
        }
      }
    }
  }
  
}