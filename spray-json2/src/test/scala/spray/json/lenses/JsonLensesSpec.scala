package spray.json
package lenses

import org.specs2.mutable.Specification
import spray.json.DeserializationException

class JsonLensesSpec extends Specification with SpecHelpers {

  import JsonLenses._

  val n = field("n")

  "Lenses" should {
    "access" in {
      "field" in {
        "existing" in {
          """{"n": 2}""".extract[Int]('n) must be_==(2)
        }
        "missing" in {
          """{"n": 2}""".extract[Int]('z) must throwAn[Exception]("""Expected field 'z' in '{"n":2}'""")
        }
        "wrong type" in {
          """{"n": 2}""".extract[String]('n) must throwA[DeserializationException]("Expected String as JsString, but got 2")
        }
      }
      "field of member" in {
        """{"n": {"b": 4}}""".extract[Int]("n" / "b") must be_==(4)
      }
      "element of array" in {
        "existing" in {
          """["a", "b", 2, 5, 8, 3]""".extract[Int](element(3)) must be_==(5)
        }
        "out of bounds" in {
          """["a", "b", 2, 5, 8, 3]""".extract[Int](element(38)) must throwAn[IndexOutOfBoundsException]("Too little elements in array: [\"a\",\"b\",2,5,8,3] size: 6 index: 38")
        }
      }
      "finding an element" in {
        "in a homogenous array" in {
          "if type matches" in {
            """[18, 23, 2, 5, 8, 3]""".extract[Int](JsonLenses.find(JsonLenses.value.is[Int](_ < 4))) must beSome(2)
          }
          "if type is wrong" in {
            """[18, 23, 2, 5, 8, 3]""".extract[Int](JsonLenses.find(JsonLenses.value.is[String](_ < "unknown"))) must beNone
          }
        }
        "in an imhomogenous array" in {
          """["a", "b", 2, 5, 8, 3]""".extract[Int](JsonLenses.find(JsonLenses.value.is[Int](_ < 4))) must beSome(2)
          """["a", "b", 2, 5, 8, 3]""".extract[Int](JsonLenses.find(JsonLenses.value.is[String](_ == "unknown"))) must beNone
        }
        "nested finding" in {
          val lens = JsonLenses.find("a".is[Int](_ == 12)) / "b" / "c" / JsonLenses.find(JsonLenses.value.is[Int](_ == 5))

          "existing" in {
            """[{"a": 12, "b": {"c": [2, 5]}}, 13]""".extract[Int](lens) must beSome(5)
          }
          "missing in first find" in {
            """[{"a": 2, "b": {"c": [5]}}, 13]""".extract[Int](lens) must beNone
          }
          "missing in second find" in {
            """[{"a": 2, "b": {"c": [7]}}, 13]""".extract[Int](lens) must beNone
          }
        }
      }
      "all elements of an array" in {
        "simple" in {
          """[18, 23, 2, 5, 8, 3]""".extract[Int](*) must be_==(Seq(18, 23, 2, 5, 8, 3))
        }
        "which is a scalar element" in {
          """{"a": [1, 2, 3, 4]}""".extract[Int](("a" / *)) must be_==(Seq(1, 2, 3, 4))
        }
        "field of an array element" in {
          """[{"a": 1}, {"a": 2}]""".extract[Int]((* / "a")) must be_==(Seq(1, 2))
        }
        "nested" in {
          """[[1, 2], [3, 4]]""".extract[Int]((* / *)) must be_==(Seq(1, 2, 3, 4))
        }
        "if outer is no array" in {
          """{"a": 5}""".extract[Int]((* / "a")) must throwAn[Exception]("""Not a json array: {"a":5}""")
          """{"a": 5}""".extract[Int]((* / *)) must throwAn[Exception]("""Not a json array: {"a":5}""")
        }
        "if inner is no array" in {
          """[{}, {}]""".extract[Int]((* / *)) must throwAn[Exception]("""Not a json array: {}""")
          """{"a": 5}""".extract[Int](("a" / *)) must throwAn[Exception]("""Not a json array: 5""")
        }
      }
      /*"filtered elements of an array" in {

      }*/
    }

    "modify" in {
      "set field" in {
        "existing" in {
          """{"n": 12}""" update (n ! set(23)) must be_json("""{"n": 23}""")
        }
        "missing" in {
          """{"n": {"b": 4}}""" update ("n" / "c" ! set(23)) must be_json("""{"n": {"b": 4, "c": 23}}""")
        }
        "twice" in {
          val a = field("a")
          """{"a": 5}""" update (a ! set(23) && a ! set(15)) must be_json("""{"a": 15}""")
        }
      }
      "update field" in {
        "existing" in {
          """{"n": 12}""" update (n ! modify[Int](_ + 1)) must be_json("""{"n": 13}""")
        }
        "wrong type" in {
          """{"n": 12}""" update (n ! modify[String](_ + "test")) must throwA[DeserializationException]("Expected String as JsString, but got 12")
        }
        "missing" in {
          """{"n": 12}""" update (field("z") ! modify[Int](_ + 1)) must throwAn[Exception]("""Expected field 'z' in '{"n":12}'""")
        }
      }
      "set field of member" in {
        """{"n": {"b": 4}}""" update ("n" / "b" ! set(23)) must be_json("""{"n": {"b": 23}}""")
      }
      "update field of member" in {
        "existing" in {
          """{"n": {"b": 4}}""" update ("n" / "b" ! modify[Int](1 +)) must be_json("""{"n": {"b": 5}}""")
        }
        "parent missing" in {
          """{"x": {"b": 4}}""" update ("n" / "b" ! modify[Int](1 +)) must throwAn[Exception]("""Expected field 'n' in '{"x":{"b":4}}'""")
        }
      }
      "set element of array" in {
        """["a", "b", 2, 5, 8, 3]""" update (element(3) ! set(35)) must be_json("""["a", "b", 2, 35, 8, 3]""")
      }
      "change a found element" in {
        "in a homogenuous array" in {
          "if found" in {
            """[12, 39, 2, 5, 8, 3]""" update (JsonLenses.find(JsonLenses.value.is[Int](_ < 4)) ! set("test")) must be_json(
              """[12, 39, "test", 5, 8, 3]""")
          }
          "if not found" in {
            """[12, 39, 2, 5, 8, 3]""" update (JsonLenses.find(JsonLenses.value.is[Int](_ == 434)) ! set("test")) must be_json(
              """[12, 39, 2, 5, 8, 3]""")
          }
        }
        "in an inhomogenuous array" in {
          """["a", "b", 2, 5, 8, 3]""" update (JsonLenses.find(JsonLenses.value.is[Int](_ < 4)) ! set("test")) must be_json(
            """["a", "b", "test", 5, 8, 3]""")
        }
        "nested" in {
          val lens = JsonLenses.find("a".is[Int](_ == 12)) / "b" / "c" / JsonLenses.find(JsonLenses.value.is[Int](_ == 5))

          "existing" in {
            """[{"a": 12, "b": {"c": [2, 5]}}, 13]""" update (lens ! set(42)) must be_json("""[{"a": 12, "b": {"c": [2, 42]}}, 13]""")
          }
          "missing in first find" in {
            """[{"a": 2, "b": {"c": [5]}}, 13]""" update (lens ! set(42)) must be_json("""[{"a": 2, "b": {"c": [5]}}, 13]""")
          }
          "missing in second find" in {
            """[{"a": 2, "b": {"c": [7]}}, 13]""" update (lens ! set(42)) must be_json("""[{"a": 2, "b": {"c": [7]}}, 13]""")
          }
        }
      }
    }
  }
}

