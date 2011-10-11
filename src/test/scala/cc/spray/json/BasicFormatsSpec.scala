package cc.spray.json

import org.specs2.mutable._

class BasicFormatsSpec extends Specification with DefaultJsonProtocol {

  "The IntJsonFormat" should {
    "convert an Int to a JsNumber" in {
      42.toJson mustEqual JsNumber(42)
    }
    "convert a JsNumber to an Int" in {
      JsNumber(42).fromJson[Int] mustEqual 42 
    }
  }
  
  "The LongJsonFormat" should {
    "convert a Long to a JsNumber" in {
      42L.toJson mustEqual JsNumber(42L)
    }
    "convert a JsNumber to a Long" in {
      JsNumber(42L).fromJson[Long] mustEqual 42L 
    }
  }
  
  "The FloatJsonFormat" should {
    "convert a Float to a JsNumber" in {
      4.2f.toJson mustEqual JsNumber(4.2f)
    }
    "convert a Float.NaN to a JsNull" in {
      Float.NaN.toJson mustEqual JsNull
    }
    "convert a Float.PositiveInfinity to a JsNull" in {
      Float.PositiveInfinity.toJson mustEqual JsNull
    }
    "convert a Float.NegativeInfinity to a JsNull" in {
      Float.NegativeInfinity.toJson mustEqual JsNull
    }
    "convert a JsNumber to a Float" in {
      JsNumber(4.2f).fromJson[Float] mustEqual 4.2f 
    }
  }
  
  "The DoubleJsonFormat" should {
    "convert a Double to a JsNumber" in {
      4.2.toJson mustEqual JsNumber(4.2)
    }
    "convert a Double.NaN to a JsNull" in {
      Double.NaN.toJson mustEqual JsNull
    }
    "convert a Double.PositiveInfinity to a JsNull" in {
      Double.PositiveInfinity.toJson mustEqual JsNull
    }
    "convert a Double.NegativeInfinity to a JsNull" in {
      Double.NegativeInfinity.toJson mustEqual JsNull
    }
    "convert a JsNumber to a Double" in {
      JsNumber(4.2).fromJson[Double] mustEqual 4.2 
    }
  }
  
  "The ByteJsonFormat" should {
    "convert a Byte to a JsNumber" in {
      42.asInstanceOf[Byte].toJson mustEqual JsNumber(42)
    }
    "convert a JsNumber to a Byte" in {
      JsNumber(42).fromJson[Byte] mustEqual 42 
    }
  }
  
  "The ShortJsonFormat" should {
    "convert a Short to a JsNumber" in {
      42.asInstanceOf[Short].toJson mustEqual JsNumber(42)
    }
    "convert a JsNumber to a Short" in {
      JsNumber(42).fromJson[Short] mustEqual 42 
    }
  }
  
  "The BigDecimalJsonFormat" should {
    "convert a BigDecimal to a JsNumber" in {
      BigDecimal(42).toJson mustEqual JsNumber(42)
    }
    "convert a JsNumber to a BigDecimal" in {
      JsNumber(42).fromJson[BigDecimal] mustEqual BigDecimal(42) 
    }
  }
  
  "The BigIntJsonFormat" should {
    "convert a BigInt to a JsNumber" in {
      BigInt(42).toJson mustEqual JsNumber(42)
    }
    "convert a JsNumber to a BigInt" in {
      JsNumber(42).fromJson[BigInt] mustEqual BigInt(42) 
    }
  }
  
  "The UnitJsonFormat" should {
    "convert Unit to a JsNumber(1)" in {
      ().toJson mustEqual JsNumber(1)
    }
    "convert a JsNumber to Unit" in {
      JsNumber(1).fromJson[Unit] mustEqual () 
    }
  }
  
  "The BooleanJsonFormat" should {
    "convert true to a JsTrue" in { true.toJson mustEqual JsTrue }
    "convert false to a JsFalse" in { false.toJson mustEqual JsFalse }
    "convert a JsTrue to true" in { JsTrue.fromJson[Boolean] mustEqual true }
    "convert a JsFalse to false" in { JsFalse.fromJson[Boolean] mustEqual false }
  }
  
  "The CharJsonFormat" should {
    "convert a Char to a JsString" in {
      'c'.toJson mustEqual JsString("c")
    }
    "convert a JsString to a Char" in {
      JsString("c").fromJson[Char] mustEqual 'c' 
    }
  }
  
  "The StringJsonFormat" should {
    "convert a String to a JsString" in {
      "Hello".toJson mustEqual JsString("Hello")
    }
    "convert a JsString to a String" in {
      JsString("Hello").fromJson[String] mustEqual "Hello" 
    }
  }
  
  "The SymbolJsonFormat" should {
    "convert a Symbol to a JsString" in {
      'Hello.toJson mustEqual JsString("Hello")
    }
    "convert a JsString to a Symbol" in {
      JsString("Hello").fromJson[Symbol] mustEqual 'Hello 
    }
  }
  
}
