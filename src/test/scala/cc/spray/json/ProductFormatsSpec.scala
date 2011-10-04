package cc.spray.json

import org.specs2.mutable._

class ProductFormatsSpec extends Specification {

  case class Test2(a: Int, b: Option[Double])
  case class Test3[A, B](as: List[A], bs: List[B])

  trait TestProtocol {
    this: DefaultJsonProtocol =>
    implicit val test2Format = jsonFormat(Test2, "a", "b")
    implicit def test3Format[A: JsonFormat, B: JsonFormat] = jsonFormat(Test3.apply[A, B], "as", "bs")
  }
  object TestProtocol1 extends DefaultJsonProtocol with TestProtocol
  object TestProtocol2 extends DefaultJsonProtocol with TestProtocol with NullOptions

  "A JsonFormat created with `jsonFormat`, for a case class with 2 elements," should {
    import TestProtocol1._
    val obj = Test2(42, Some(4.2))
    val json = JsObject(JsField("a", 42), JsField("b", 4.2))
    "convert to a respective JsObject" in {
      obj.toJson mustEqual json
    }
    "convert a JsObject to the respective case class instance" in {
      json.fromJson[Test2] mustEqual obj
    }
    "throw a DeserializationException if the JsObject does not all required members" in (
      JsObject(JsField("b", 4.2)).fromJson[Test2] must
              throwA(new DeserializationException("Object is missing required member 'a'"))
    )
    "not require the presence of optional fields for deserialization" in {
      JsObject(JsField("a", 42)).fromJson[Test2] mustEqual Test2(42, None)
    }
    "not render `None` members during serialization" in {
      Test2(42, None).toJson mustEqual JsObject(JsField("a", 42))
    }
    "ignore additional members during deserialization" in {
      JsObject(JsField("a", 42), JsField("b", 4.2), JsField("c", 'no)).fromJson[Test2] mustEqual obj 
    }
    "not depend on any specific member order for deserialization" in {
      JsObject(JsField("b", 4.2), JsField("a", 42)).fromJson[Test2] mustEqual obj
    }
    "throw a DeserializationException if the JsValue is not a JsObject" in (
      JsNull.fromJson[Test2] must throwA(new DeserializationException("Object expected"))  
    )
  }

  "A JsonProtocol mixing in NullOptions" should {
    "render `None` members to `null`" in {
      import TestProtocol2._
      Test2(42, None).toJson mustEqual JsObject(JsField("a", 42), JsField("b", JsNull))
    }
  }

  "A JsonFormat for a generic case class and created with `jsonFormat`" should {
    import TestProtocol1._
    val obj = Test3(42 :: 43 :: Nil, "x" :: "y" :: "z" :: Nil)
    val json = JsObject(
      JsField("as", JsArray(JsNumber(42), JsNumber(43))),
      JsField("bs", JsArray(JsString("x"), JsString("y"), JsString("z")))
    )
    "convert to a respective JsObject" in {
      obj.toJson mustEqual json
    }
    "convert a JsObject to the respective case class instance" in {
      json.fromJson[Test3[Int, String]] mustEqual obj
    }
  }

}
