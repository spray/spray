package spray.httpx

import java.lang.reflect.InvocationTargetException

import spray.http.{ ContentTypes, HttpCharsets, HttpEntity, MediaTypes }
import spray.httpx.unmarshalling.Unmarshaller
import spray.httpx.marshalling.Marshaller

import org.json4s.Serialization
import org.json4s.{ Formats, MappingException }

trait BaseJson4sSupport {
  implicit def json4sFormats: Formats
  protected[httpx] def serialization: Serialization

  implicit def json4sUnmarshaller[T: Manifest] =
    Unmarshaller[T](MediaTypes.`application/json`) {
      case x: HttpEntity.NonEmpty ⇒
        try serialization.read[T](x.asString(defaultCharset = HttpCharsets.`UTF-8`))
        catch {
          case MappingException("unknown error", ite: InvocationTargetException) ⇒ throw ite.getCause
        }
    }

  implicit def json4sMarshaller[T <: AnyRef] =
    Marshaller.delegate[T, String](ContentTypes.`application/json`)(serialization.write(_))
}
