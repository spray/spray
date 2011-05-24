package cc.spray.json

import org.specs.Specification

class ProductFormatsSpec extends Specification with DefaultJsonProtocol {

  case class Test2(a: Int, b: Double)
  implicit val test2Format = jsonFormat(Test2, "a", "b") 
  
  "A JsonFormat created with format, for a case class with 2 elements," should {
    val obj = Test2(42, 4.2)
    val json = JsObject(JsField("a", 42), JsField("b", 4.2))
    "convert to a respective JsObject" in {
      obj.toJson mustEqual json
    }
    "convert a JsObject to the respective case class instance" in {
      json.fromJson[Test2] mustEqual obj 
    }
    "throw a DeserializationException if the JsObject does not define the right members" in (
      JsObject(JsField("a", 42), JsField("x", 4.2)).fromJson[Test2] must
              throwA(new DeserializationException("Object is missing required member 'b'"))  
    )
    "ignore additional members during deserialization" in {
      JsObject(JsField("a", 42), JsField("b", 4.2), JsField("c", 'no)).fromJson[Test2] mustEqual obj 
    }
    "throw a DeserializationException if the JsValue is not a JsObject" in (
      JsNull.fromJson[Test2] must throwA(new DeserializationException("Object expected"))  
    )
  }
  
}
