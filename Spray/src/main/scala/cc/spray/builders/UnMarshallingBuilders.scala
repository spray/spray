package cc.spray
package builders

import http._
import HttpStatusCodes._
import marshalling._

private[spray] trait UnMarshallingBuilders extends DefaultMarshallers with DefaultUnmarshallers {
  this: BasicBuilders =>
  
  def service(route: Route)(implicit marshaller: Marshaller): RootRoute = new RootRoute({ ctx =>
    route {
      ctx.withHttpResponseTransformed { response =>
        if (response.isSuccess) {
          val accept = ctx.request.isContentTypeAccepted(_) 
          response.content match {
            case EmptyContent => response 
            case ObjectContent(x) => x.marshal(accept)(marshaller) match {
              case Right(rawContent) => response.copy(content = rawContent)
              case Left(httpStatus) => HttpResponse(httpStatus) 
            }
            case x: BufferContent => if (accept(x.contentType)) response else {
              HttpResponse(HttpStatus(InternalServerError, "Response BufferContent has unacceptable Content-Type"))
            }
          }
        } else {
          response // do not change the response of failures or warnings
        }
      } 
    }
  })
  
  def contentAs[A: Manifest : Unmarshaller](route: Route): Route = { ctx =>
    ctx.request.content.as[A] match {
      case Right(a) => route(ctx.withRequestTransformed(_.copy(content = ObjectContent(a))))
      case Left(httpStatus) => ctx.fail(httpStatus)
    }
  }
  
  def getContentAs[A: Manifest : Unmarshaller](routing: A => Route): Route = { ctx =>
    ctx.request.content.as[A] match {
      case Right(a) => routing(a)(ctx)
      case Left(httpStatus) => ctx.fail(httpStatus)
    }
  }          
  
  def handledBy[A: Manifest : Unmarshaller](f: A => Any): Route = {
    getContentAs[A] { input =>
       _.complete {
        f(input) match {
          case null | EmptyContent => EmptyContent
          case x: BufferContent => x
          case x => ObjectContent(x)
        }
      }
    }
  }
  
}