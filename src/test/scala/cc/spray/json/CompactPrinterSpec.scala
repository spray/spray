package cc.spray.json

import org.specs2.mutable._

class CompactPrinterSpec extends Specification {

  "The CompactPrinter" should {
    "print JsNull to 'null'" in {
      CompactPrinter(JsNull) mustEqual "null"
    }
    "print JsTrue to 'true'" in {
      CompactPrinter(JsTrue) mustEqual "true"
    }
    "print JsFalse to 'false'" in {
      CompactPrinter(JsFalse) mustEqual "false"
    }
    "print JsNumber(0) to '0'" in {
      CompactPrinter(JsNumber(0)) mustEqual "0"
    }
    "print JsNumber(1.23) to '1.23'" in {
      CompactPrinter(JsNumber(1.23)) mustEqual "1.23"
    }
    "print JsNumber(1.23) to '1.23'" in {
      CompactPrinter(JsNumber(1.23)) mustEqual "1.23"
    }
    "print JsNumber(-1E10) to '-1E10'" in {
      CompactPrinter(JsNumber(-1E10)) mustEqual "-1.0E+10"
    }
    "print JsNumber(12.34e-10) to '12.34e-10'" in {
      CompactPrinter(JsNumber(12.34e-10)) mustEqual "1.234E-9"
    }
    "print JsString(\"xyz\") to \"xyz\"" in {
      CompactPrinter(JsString("xyz")) mustEqual "\"xyz\""
    }
    "properly escape special chars in JsString" in {
      CompactPrinter(JsString("\"\\\b\f\n\r\t\u12AB")) mustEqual """"\"\\\b\f\n\r\t""" + "\\u12ab\""
    }
    "properly print a simple JsObject" in (
      CompactPrinter(JsObject(JsField("key", 42), JsField("key2", "value")))
              mustEqual """{"key":42,"key2":"value"}"""
    )
    "properly print a simple JsArray" in (
      CompactPrinter(JsArray(JsNull, JsNumber(1.23), JsObject(JsField("key", true))))
              mustEqual """[null,1.23,{"key":true}]"""
    )
    "properly print a JSON padding (JSONP) if requested" in {
      CompactPrinter(JsTrue, Some("customCallback")) mustEqual("customCallback(true)")
    }
  }
  
}