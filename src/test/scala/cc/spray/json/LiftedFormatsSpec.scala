package cc.spray.json

import org.specs2.mutable._
import cc.spray.json.DefaultJsonProtocol._

/**
 * User: dirk
 * Date: 31-08-11
 * Time: 10:01
 */

class LiftedFormatsSpec extends Specification {

  case class Container[A](obj: Option[A])

  implicit def containerWriter[T](implicit writer: JsonWriter[T]) = new JsonWriter[Container[T]] {
    import LiftedFormats.liftJsonWriter
    def write(obj: Container[T]): JsValue = JsObject(JsField("content", obj.obj.toJson))
  }

  implicit def containerReader[T](implicit reader: JsonReader[T]) = new JsonReader[Container[T]] {
    import LiftedFormats.liftJsonReader
    def read(value: JsValue): Container[T] = {
      value match {
        case JsObject(JsField("content", obj: JsValue) :: Nil) => Container(Some(reader.read(obj)))
        case _ => throw new DeserializationException("Unexpected format: " + value.toString)
      }
    }
  }

  val obj = Container(Some(Container(Some(List(1, 2, 3)))))

  "The liftJsonWriter" should {
    "convert a Container[Container[List[Int]]] to JsValue and back" in {
      val r = obj.toJson
      r.fromJson[Container[Container[List[Int]]]]
      ok
    }
  }
}