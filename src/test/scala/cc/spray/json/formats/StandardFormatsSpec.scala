package cc.spray.json
package formats

import org.specs.Specification

class StandardFormatsSpec extends Specification with StandardFormats with BasicFormats {

  "The optionFormat" should {
    "convert None to JsNull" in {
      None.asInstanceOf[Option[Int]].toJson mustEqual JsNull
    }
    "convert JsNull to None" in {
      JsNull.fromJson[Option[Int]] mustEqual None
    } 
    "convert Some(Hello) to JsString(Hello)" in {
      Some("Hello").asInstanceOf[Option[String]].toJson mustEqual JsString("Hello")
    }
    "convert JsString(Hello) to Some(Hello)" in {
      JsString("Hello").fromJson[Option[String]] mustEqual Some("Hello")
    } 
  }
  
  "The tuple1Format" should {
    "convert (42) to a JsNumber" in {
      Tuple1(42).toJson mustEqual JsNumber(42)
    }
    "be able to convert a JsNumber to a Tuple1[Int]" in {
      JsNumber(42).fromJson[Tuple1[Int]] mustEqual Tuple1(42) 
    }
  }
  
  "The tuple2Format" should {
    val json = JsArray(JsNumber(42), JsNumber(4.2))
    "convert (42, 4.2) to a JsArray" in {
      (42, 4.2).toJson mustEqual json
    }
    "be able to convert a JsArray to a (Int, Double)]" in {
      json.fromJson[(Int, Double)] mustEqual (42, 4.2) 
    }
  }
  
}