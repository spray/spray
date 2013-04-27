package spray.json

import org.specs2.mutable.Specification

class BuilderApiSpec extends Specification {
  "JsValues by DSL" should {
    "build object from tuples" in {
      "one tuple" in {
        ("a" -> 4).toJson must be_==(JsObject("a" -> JsNumber(4)))
        ('b -> 13).toJson must be_==(JsObject("b" -> JsNumber(13)))
      }
      "two fields" in {
        ("a" -> 5) ~ ('b -> 12) must be_==(JsObject("a" -> JsNumber(5), "b" -> JsNumber(12)))
      }
      "three fields" in {
        ("a" -> 5) ~ ("b" -> 12) ~ ('c -> 15) must be_==(JsObject("a" -> JsNumber(5),
          "b" -> JsNumber(12),
          "c" -> JsNumber(15)))
      }
      "nested" in {
        ("a" -> 4) ~ ('b -> (5, 'd -> true)) must be_==(
          JsObject("a" -> JsNumber(4),
            "b" -> JsArray(JsNumber(5), JsObject("d" -> JsTrue))))
      }
    }
  }
}
