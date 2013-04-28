package spray.json

import org.specs2.mutable.Specification

class InterpolatorSpec extends Specification {
  import JsonInterpolator._

  implicit def JsValueWriter[T <: JsValue] = new JsonWriter[T] {
    def write(obj: T): JsValue = if (obj == null) JsNull else obj
  }

  "The json interpolator" should {
    "interpolate" in {
      "a constant JsValue" in {
        "string" in {
          json""""test"""" must be_==(JsonParser(""""test""""))
        }
        "number" in {
          json"""5.2""" must be_==(JsonParser("""5.2"""))
        }
        "null" in {
          json"""null""" must be_==(JsonParser("""null"""))
        }
        "boolean" in {
          json"""true""" must be_==(JsonParser("""true"""))
          json"""false""" must be_==(JsonParser("""false"""))
        }
        "array" in {
          json"""[1,"2", 3]""" must be_==(JsonParser("""[1,"2", 3]"""))
        }
        "object" in {
          val json = json"""{ "a": 5, "b": "test", "c": null, "d": true, "e": [1, 2, 3] }"""
          json must be_==(JsonParser("""{ "a": 5, "b": "test", "c": null, "d": true, "e": [1, 2, 3] }"""))
        }
      }
      "primitive values" in {
        "strings" in {
          json"""${"test"}""" must be_==(JsonParser(""""test""""))
        }
        "numbers" in {
          json"""${5}""" must be_==(JsonParser("""5"""))
          json"""${55.2}""" must be_==(JsonParser("""55.2"""))
        }
        "null" in {
          val x: JsValue = (null: String).toJson
          //println(x.getClass.getSimpleName, x)
          json"""${null: JsObject}""" must be_==(JsNull)
        }
        "boolean" in {
          json"""${false}""" must be_==(JsonParser("""false"""))
          json"""${true}""" must be_==(JsonParser("""true"""))
        }
        "JsValue" in {
          val x = JsString("Test")
          json"""{"a": $x}""" must be_==(JsonParser("""{"a": "Test"}"""))
        }
      }
      "an array element" in {
        json"""[5, "a", ${12}, "b"]""" must be_==(JsonParser("""[5, "a", 12, "b"]"""))
      }
      "a field name" in {
        val x = "num"
        json"""{$x: 5}""" must be_==(JsonParser("""{"num": 5 }"""))
      }
      "a field value" in {
        val x = 5.5
        json"""{"grade": $x}""" must be_==(JsonParser("""{"grade": 5.5}"""))
      }
      /*"an option field value" in {
        val x: Option[Double] = Some(5.5)
        val y: Option[Double] = None
        json"""{"grade": $x}""" must be_==(JsonParser("""{"grade": 5.5}"""))
        json"""{"grade": $y}""" must be_==(JsonParser("""{}"""))
      }*/

      "multiple array elements" in { pending }
      "multiple key values" in { pending }

    }
  }
}
