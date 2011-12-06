package cc.spray.json

import org.specs2.mutable._

class AdditionalFormatsSpec extends Specification {

  case class Container[A](inner: Option[A])

  object ReaderProtocol extends DefaultJsonProtocol {
    implicit def containerReader[T :JsonFormat] = lift {
      new JsonReader[Container[T]] {
        def read(value: JsValue) = value match {
          case JsObject(fields) if fields.contains("content") => Container(Some(jsonReader[T].read(fields("content"))))
          case _ => throw new DeserializationException("Unexpected format: " + value.toString)
        }
      }
    }
  }

  object WriterProtocol extends DefaultJsonProtocol {
    implicit def containerWriter[T :JsonFormat] = lift {
      new JsonWriter[Container[T]] {
        def write(obj: Container[T]) = JsObject("content" -> obj.inner.toJson)
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
      JsonParser("""{"content":{"content":[1,2,3]}}""").convertTo[Container[Container[List[Int]]]] mustEqual obj
    }
  }

  case class Foo(id: Long, name: String, foos: Option[List[Foo]] = None)

  object FooProtocol extends DefaultJsonProtocol {
    implicit val FooProtocol: JsonFormat[Foo] = lazyFormat(jsonFormat(Foo, "id", "name", "foos"))
  }

  "The lazyFormat wrapper" should {
    "enable recursive format definitions" in {
      import FooProtocol._
      Foo(1, "a", Some(Foo(2, "b", Some(Foo(3, "c") :: Nil)) :: Foo(4, "d") :: Nil)).toJson.toString mustEqual
        """{"id":1,"name":"a","foos":[{"id":2,"name":"b","foos":[{"id":3,"name":"c"}]},{"id":4,"name":"d"}]}"""
    }
  }
}