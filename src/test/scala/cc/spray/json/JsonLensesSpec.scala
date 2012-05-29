package cc.spray.json

import DefaultJsonProtocol._

import org.specs2.mutable.Specification

class JsonLensesSpec extends Specification {
  val json = JsonParser(
    """{
      |  "n": 2,
      |  "els": [
      |     {
      |       "name": "John",
      |       "money": 23
      |     },
      |     {
      |       "name": "Paul",
      |       "money": 42
      |     }
      |  ]
      |}
    """.stripMargin)

  import JsonLenses._

  val n = field("n")

  "Lenses" should {
    "access" in {
      "field" in {
        "n".get[Int].apply(json) must be_==(2)
      }
      "field of member" in {
        val json = JsonParser("""{"n": {"b": 4}}""")

        ("n" / "b").get[Int].apply(json) must be_==(4)
      }
      "element of array" in {
        val json = JsonParser("""["a", "b", 2, 5, 8, 3]""")

        json extract element(3).get[Int] must be_==(5)
      }
    }

    "modify" in {
      "set field" in {
        val simple = JsonParser( """{"n": 12}""")

        simple.update(n ! set(23)) must be_json( """{"n": 23}""")
      }
      "update field" in {
        val simple = JsonParser( """{"n": 12}""")
        simple.update(n ! updated[Int](_ + 1)) must be_json( """{"n": 13}""")
      }
      "set field of member" in {
        val json = JsonParser( """{"n": {"b": 4}}""")

        json update ("n" / "b" ! set(23)) must be_json( """{"n": {"b": 23}}""")
      }
      "update field of member" in {
        val json = JsonParser( """{"n": {"b": 4}}""")

        json update ("n" / "b" ! updated[Int](1 +)) must be_json( """{"n": {"b": 5}}""")
      }
      "set element of array" in {
        val json = JsonParser("""["a", "b", 2, 5, 8, 3]""")

        json update (element(3) ! set(35)) must be_json("""["a", "b", 2, 35, 8, 3]""")
      }
    }
  }

  def be_json(json: String) =
    be_==(JsonParser(json))
}
