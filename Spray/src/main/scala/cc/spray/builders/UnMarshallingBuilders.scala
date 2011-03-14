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
          // do not change the response of failures or warnings
          response
        }
      } 
    }
  })
  
  def handledBy[A, B](f: A => B)(implicit ma: Manifest[A], unmarshaller: Unmarshaller[A]): Route = { ctx =>
    ctx.request.content.as[A](ma, unmarshaller) match {
      case Right(input) => ctx.respond {
        f(input) match {
          case EmptyContent => EmptyContent
          case x: BufferContent => x
          case x => ObjectContent(x)
        }
      }
      case Left(httpStatus) => ctx.respond(httpStatus)
    }    
  }
  
}