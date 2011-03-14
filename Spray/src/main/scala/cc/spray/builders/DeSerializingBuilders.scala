package cc.spray
package builders

import http._
import HttpStatusCodes._
import marshalling._

private[spray] trait DeSerializingBuilders extends DefaultMarshallers with DefaultUnmarshallers {
  this: BasicBuilders =>
  
  // TODO: change marshallers and unmarshallers to actors
  
  def service(route: Route)(implicit marshallers: List[Marshaller]): RootRoute = new RootRoute({ ctx =>
    def marshal(response: HttpResponse, obj: Any): HttpResponse = {
      marshallers.mapFind { marshaller =>
        marshaller.canMarshal(obj).mapFind { contentType =>
          if (ctx.request.isContentTypeAccepted(contentType)) Some((marshaller, contentType)) else None
        }
      } match {
        case Some((marshaller, contentType)) => {
          response.copy(content = marshaller.marshal(obj, contentType))
        }
        case None => HttpResponse(HttpStatus(NotAcceptable, "Resource representation is only available with these " +
                "content-types:\n" + marshallers.flatMap(_.canMarshal(obj)).mkString("\n")))
      }
    }
          
    route {
      ctx.withHttpResponseTransformed { response =>
        if (response.isSuccess) {
          response.content match {
            case EmptyContent => response 
            case ObjectContent(x) => marshal(response, x)
            case x: BufferContent => if (ctx.request.isContentTypeAccepted(x.contentType)) response else {
              HttpResponse(HttpStatus(InternalServerError, "Cannot marshal BufferContent"))
            }
          }
        } else {
          // do not change the response of failures or warnings
          response
        }
      } 
    }
  })
  
  def handledBy[A, B](f: A => B)(implicit ma: Manifest[A], unmarshallers: List[Unmarshaller[A]]): Route = { ctx =>
    def unmarshal(bufferContent: BufferContent): Either[HttpStatus, A] = {
      unmarshallers.mapFind { unmarshaller =>
        unmarshaller.canUnmarshalFrom.mapFind { contentType =>
          if (contentType.equalsOrIncludes(bufferContent.contentType)) Some(unmarshaller) else None
        }
      } match {
        case Some(unmarshaller) => Right(unmarshaller.unmarshal(bufferContent))
        case None => {
          Left(HttpStatus(UnsupportedMediaType, "The requests content-type must be one the following:\n" + 
                  unmarshallers.flatMap(_.canUnmarshalFrom).mkString("\n")))
        }
      }
    }
    
    val input = ctx.request.content match {
      case x: BufferContent => unmarshal(x)
      case ObjectContent(x) => {
        if (ma.erasure.isInstance(x)) Right(x.asInstanceOf[A])
        else Left(HttpStatus(InternalServerError, "Cannot unmarshal ObjectContent"))
      }
      case EmptyContent => {
        if (ma.erasure == classOf[Unit]) Right(().asInstanceOf[A])
        else Left(HttpStatus(BadRequest, "Request entity expected"))
      }
    }
    input match {
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