package cc.spray

import builders._
import http._

trait ServiceBuilder
        extends CachingBuilders
        with DetachedBuilders
        with FileResourceDirectoryBuilders
        with FilterBuilders
        with ParameterBuilders
        with PathBuilders
        with SimpleFilterBuilders
        with UnMarshallingBuilders {
  
  // uncachable
  def hardFail(failure: HttpFailure, reason: String = ""): Nothing = throw new HttpException(failure, reason)
  
  implicit def pimpRouteWithConcatenation(route: Route): { def ~ (other: Route): Route } = new {
    def ~ (other: Route): Route = { ctx =>
      route {
        ctx.withResponder { 
          _ match {
            case x@ Right(response) => ctx.responder(x) // first route succeeded
            case Left(rejections1) => other {
              ctx.withResponder {
                _ match {
                  case x@ Right(_) => ctx.responder(x) // second route succeeded
                  case Left(rejections2) => ctx.responder(Left(rejections1 ++ rejections2))  
                }
              }
            }  
          }
        }
      }
    }
  }
  
}