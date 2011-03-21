package cc.spray
package builders

import marshalling._

private[spray] trait UnMarshallingBuilders extends DefaultMarshallers with DefaultUnmarshallers {
  this: FilterBuilders =>
  
  def contentAs[A :Unmarshaller](routing: A => Route): Route = {
    val filterRoute = filter1 { ctx =>
      ctx.request.content.as[A] match {
        case Right(a) => Pass(a :: Nil)
        case Left(rejection) => Reject(rejection)
      }
    }
    filterRoute(routing) 
  }
  
  def produces[A](routing: (A => Unit) => Route)(implicit marshaller: Marshaller[A]): Route = {
    val filterRoute = filter1 { ctx =>
      marshaller(ctx.request.isContentTypeAccepted(_)) match {
        case MarshalWith(converter) => Pass({ (a: A) => ctx.complete(converter(a)) } :: Nil)
        case CantMarshal(onlyTo) => Reject(UnacceptedResponseContentTypeRejection(onlyTo))
      }
    }
    filterRoute(routing)
  }
  
  def handledBy[A :Unmarshaller, B: Marshaller](f: A => B): Route = {
    contentAs[A] { a =>
      produces[B] { produce =>
        _ => produce(f(a))
      }
    }
  }
  
}