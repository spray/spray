package cc.spray.json

import org.specs.Specification

class PrettyPrinterSpec extends Specification {

  "The PrettyPrinter" should {
    "print a more complicated JsObject nicely aligned" in {
      PrettyPrinter {
        JsonParser {
          """|{
             |  "simpleKey" : "some value",
             |  "key with spaces": null,
             |  "zero": 0,
             |  "number": -1.2323424E-5,
             |  "Boolean yes":true,
             |  "Boolean no": false,
             |  "Unic\u00f8de" :  "Long string with newline\nescape",
             |  "key with \"quotes\"" : "string",
             |  "sub object" : {
             |    "sub key": 26.5,
             |    "a": "b",
             |    "array": [1, 2, { "yes":1, "no":0 }, ["a", "b", null], false]
             |  }
             |}""".stripMargin
        }
      } mustEqual {
        """|{
           |  "simpleKey": "some value",
           |  "key with spaces": null,
           |  "zero": 0,
           |  "number": -0.000012323424,
           |  "Boolean yes": true,
           |  "Boolean no": false,
           |  "Unic\u00f8de": "Long string with newline\nescape",
           |  "key with \"quotes\"": "string",
           |  "sub object": {
           |    "sub key": 26.5,
           |    "a": "b",
           |    "array": [1, 2, {
           |      "yes": 1,
           |      "no": 0
           |    }, ["a", "b", null], false]
           |  }
           |}""".stripMargin.replace("\u00f8", "\\u00f8")
      }
    }
  }
  
}