package spray.httpx

import org.json4s.MappingException
import java.lang.reflect.InvocationTargetException

trait BaseJson4sSupport {
  def unpackExceptions[T](body: ⇒ T): T =
    try body
    catch {
      case MappingException("unknown error", ite: InvocationTargetException) ⇒ throw ite.getCause
    }
}
