package cc.spray.json

import org.specs2.mutable._
import scala.Right

class StandardFormatsSpec extends Specification with DefaultJsonProtocol {

  "The optionFormat" should {
    "convert None to JsNull" in {
      None.asInstanceOf[Option[Int]].toJson mustEqual JsNull
    }
    "convert JsNull to None" in {
      JsNull.convertTo[Option[Int]] mustEqual None
    } 
    "convert Some(Hello) to JsString(Hello)" in {
      Some("Hello").asInstanceOf[Option[String]].toJson mustEqual JsString("Hello")
    }
    "convert JsString(Hello) to Some(Hello)" in {
      JsString("Hello").convertTo[Option[String]] mustEqual Some("Hello")
    } 
  }

  "The eitherFormat" should {
    val a: Either[Int, String] = Left(42)
    val b: Either[Int, String] = Right("Hello")

    "convert the left side of an Either value to Json" in {
      a.toJson mustEqual JsNumber(42)
    }
    "convert the right side of an Either value to Json" in {
      b.toJson mustEqual JsString("Hello")
    }
    "convert the left side of an Either value from Json" in {
      JsNumber(42).convertTo[Either[Int, String]] mustEqual Left(42)
    }
    "convert the right side of an Either value from Json" in {
      JsString("Hello").convertTo[Either[Int, String]] mustEqual Right("Hello")
    }
  }
  
  "The tuple1Format" should {
    "convert (42) to a JsNumber" in {
      Tuple1(42).toJson mustEqual JsNumber(42)
    }
    "be able to convert a JsNumber to a Tuple1[Int]" in {
      JsNumber(42).convertTo[Tuple1[Int]] mustEqual Tuple1(42)
    }
  }
  
  "The tuple2Format" should {
    val json = JsArray(JsNumber(42), JsNumber(4.2))
    "convert (42, 4.2) to a JsArray" in {
      (42, 4.2).toJson mustEqual json
    }
    "be able to convert a JsArray to a (Int, Double)]" in {
      json.convertTo[(Int, Double)] mustEqual (42, 4.2)
    }
  }
  
}