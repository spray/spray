package cc.spray.json

import org.specs2.mutable._

class BasicFormatsSpec extends Specification with DefaultJsonProtocol {

  "The IntJsonFormat" should {
    "convert an Int to a JsNumber" in {
      42.toJson mustEqual JsNumber(42)
    }
    "convert a JsNumber to an Int" in {
      JsNumber(42).convertTo[Int] mustEqual 42
    }
  }
  
  "The LongJsonFormat" should {
    "convert a Long to a JsNumber" in {
      42L.toJson mustEqual JsNumber(42L)
    }
    "convert a JsNumber to a Long" in {
      JsNumber(42L).convertTo[Long] mustEqual 42L
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
      JsNumber(4.2f).convertTo[Float] mustEqual 4.2f
    }
    "convert a JsNull to a Float" in {
      JsNull.convertTo[Float].isNaN mustEqual Float.NaN.isNaN
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
      JsNumber(4.2).convertTo[Double] mustEqual 4.2
    }
    "convert a JsNull to a Double" in {
      JsNull.convertTo[Double].isNaN mustEqual Double.NaN.isNaN
    }
  }
  
  "The ByteJsonFormat" should {
    "convert a Byte to a JsNumber" in {
      42.asInstanceOf[Byte].toJson mustEqual JsNumber(42)
    }
    "convert a JsNumber to a Byte" in {
      JsNumber(42).convertTo[Byte] mustEqual 42
    }
  }
  
  "The ShortJsonFormat" should {
    "convert a Short to a JsNumber" in {
      42.asInstanceOf[Short].toJson mustEqual JsNumber(42)
    }
    "convert a JsNumber to a Short" in {
      JsNumber(42).convertTo[Short] mustEqual 42
    }
  }
  
  "The BigDecimalJsonFormat" should {
    "convert a BigDecimal to a JsNumber" in {
      BigDecimal(42).toJson mustEqual JsNumber(42)
    }
    "convert a JsNumber to a BigDecimal" in {
      JsNumber(42).convertTo[BigDecimal] mustEqual BigDecimal(42)
    }
  }
  
  "The BigIntJsonFormat" should {
    "convert a BigInt to a JsNumber" in {
      BigInt(42).toJson mustEqual JsNumber(42)
    }
    "convert a JsNumber to a BigInt" in {
      JsNumber(42).convertTo[BigInt] mustEqual BigInt(42)
    }
  }
  
  "The UnitJsonFormat" should {
    "convert Unit to a JsNumber(1)" in {
      ().toJson mustEqual JsNumber(1)
    }
    "convert a JsNumber to Unit" in {
      JsNumber(1).convertTo[Unit] mustEqual ()
    }
  }
  
  "The BooleanJsonFormat" should {
    "convert true to a JsTrue" in { true.toJson mustEqual JsTrue }
    "convert false to a JsFalse" in { false.toJson mustEqual JsFalse }
    "convert a JsTrue to true" in { JsTrue.convertTo[Boolean] mustEqual true }
    "convert a JsFalse to false" in { JsFalse.convertTo[Boolean] mustEqual false }
  }
  
  "The CharJsonFormat" should {
    "convert a Char to a JsString" in {
      'c'.toJson mustEqual JsString("c")
    }
    "convert a JsString to a Char" in {
      JsString("c").convertTo[Char] mustEqual 'c'
    }
  }
  
  "The StringJsonFormat" should {
    "convert a String to a JsString" in {
      "Hello".toJson mustEqual JsString("Hello")
    }
    "convert a JsString to a String" in {
      JsString("Hello").convertTo[String] mustEqual "Hello"
    }
  }
  
  "The SymbolJsonFormat" should {
    "convert a Symbol to a JsString" in {
      'Hello.toJson mustEqual JsString("Hello")
    }
    "convert a JsString to a Symbol" in {
      JsString("Hello").convertTo[Symbol] mustEqual 'Hello
    }
  }
  
}
