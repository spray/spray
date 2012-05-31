package cc.spray.json

import org.specs2.mutable.Specification

class JsonPathParserSpecs extends Specification {
  import JsonPath._

  "JsonPath parser" should {
    "simple field selection" in {
      parse("$.test") must be_==(Selection(Root, ByField("test")))
    }
    "multiple field selection" in {
      parse("$.test.test2") must be_==(Selection(Selection(Root, ByField("test")), ByField("test2")))
    }
    "wildcard selection" in {
      parse("$.a.*.c") must be_==(Selection(Selection(Selection(Root, ByField("a")), AllElements), ByField("c")))
    }
    "element selection" in {
      "of root" in {
        parse("$[2]") must be_==(Selection(Root, ByIndex(2)))
      }
      "of field" in {
        parse("$['abc']") must be_==(Selection(Root, ByField("abc")))
      }
    }
  }

  def parse(str: String) =
    JsonPathParser(str)
}
