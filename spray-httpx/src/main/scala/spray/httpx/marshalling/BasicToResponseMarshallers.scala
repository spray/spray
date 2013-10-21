package spray.httpx.marshalling

import spray.http._
import spray.http.HttpResponse
import akka.actor.ActorRef

trait BasicToResponseMarshallers {
  implicit def fromResponse: ToResponseMarshaller[HttpResponse] = ToResponseMarshaller((value, ctx) ⇒ ctx.marshalTo(value))

  // No implicit conversion to StatusCode allowed here: in `complete(i: Int)`, `i` shouldn't be marshalled
  // as a StatusCode
  implicit def fromStatusCode: ToResponseMarshaller[StatusCode] =
    fromResponse.compose(s ⇒ HttpResponse(status = s, entity = s.defaultMessage))

  implicit def fromStatusCodeAndT[S, T](implicit sConv: S ⇒ StatusCode, tMarshaller: Marshaller[T]): ToResponseMarshaller[(S, T)] =
    fromStatusCodeAndHeadersAndT[T].compose { case (s, t) ⇒ (sConv(s), Nil, t) }

  implicit def fromStatusCodeConvertibleAndHeadersAndT[S, T](implicit sConv: S ⇒ StatusCode, tMarshaller: Marshaller[T]): ToResponseMarshaller[(S, Seq[HttpHeader], T)] =
    fromStatusCodeAndHeadersAndT[T].compose { case (s, headers, t) ⇒ (sConv(s), headers, t) }

  implicit def fromStatusCodeAndHeadersAndT[T](implicit tMarshaller: Marshaller[T]): ToResponseMarshaller[(StatusCode, Seq[HttpHeader], T)] =
    new ToResponseMarshaller[(StatusCode, Seq[HttpHeader], T)] {
      def apply(value: (StatusCode, Seq[HttpHeader], T), ctx: ToResponseMarshallingContext): Unit = {
        val status = value._1
        val headers = value._2
        val mCtx = new MarshallingContext {
          def tryAccept(contentTypes: Seq[ContentType]): Option[ContentType] = ctx.tryAccept(contentTypes)
          def handleError(error: Throwable): Unit = ctx.handleError(error)
          def marshalTo(entity: HttpEntity, hs: HttpHeader*): Unit =
            ctx.marshalTo(HttpResponse(status, entity, (headers ++ hs).toList))
          def rejectMarshalling(supported: Seq[ContentType]): Unit = ctx.rejectMarshalling(supported)
          def startChunkedMessage(entity: HttpEntity, ack: Option[Any], hs: Seq[HttpHeader])(implicit sender: ActorRef): ActorRef =
            ctx.startChunkedMessage(HttpResponse(status, entity, (headers ++ hs).toList), ack)
        }
        tMarshaller(value._3, mCtx)
      }
    }
}
