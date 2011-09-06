package cc.spray.json

import org.specs2.mutable._

class AdditionalFormatsSpec extends Specification {

  case class Container[A](inner: Option[A])

  object ReaderProtocol extends DefaultJsonProtocol {
    implicit def containerReader[T :JsonFormat] = lift {
      new JsonReader[Container[T]] {
        def read(value: JsValue) = value match {
          case JsObject(JsField("content", obj: JsValue) :: Nil) => Container(Some(jsonReader[T].read(obj)))
          case _ => throw new DeserializationException("Unexpected format: " + value.toString)
        }
      }
    }
  }

  object WriterProtocol extends DefaultJsonProtocol {
    implicit def containerWriter[T :JsonFormat] = lift {
      new JsonWriter[Container[T]] {
        def write(obj: Container[T]) = JsObject(JsField("content", obj.inner.toJson))
      }
    }
  }

  "The liftJsonWriter" should {
    val obj = Container(Some(Container(Some(List(1, 2, 3)))))

    "properly write a Container[Container[List[Int]]] to JSON" in {
      import WriterProtocol._
      obj.toJson.toString mustEqual """{"content":{"content":[1,2,3]}}"""
    }

    "properly read a Container[Container[List[Int]]] from JSON" in {
      import ReaderProtocol._
      JsonParser("""{"content":{"content":[1,2,3]}}""").fromJson[Container[Container[List[Int]]]] mustEqual obj
    }
  }
}