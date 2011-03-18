package cc.spray.http
package parser

import org.specs.Specification

class QueryParserSpec extends Specification {
  
  "The QueryParser" should {
    "correctly extract complete key value pairs" in {
      QueryParser.parse("key=value") mustEqual Map("key" -> "value")
      QueryParser.parse("key=value&key2=value2") mustEqual Map("key" -> "value", "key2" -> "value2")
    }
    "return an empty Map for an empty query string" in {
      QueryParser.parse("") mustEqual Map()
    }
    "return an empty value for keys without a value following the '=' and keys without following '='" in {
      QueryParser.parse("key=&key2") mustEqual Map("key" -> "", "key2" -> "")
    }
    "throw an HttpException for illegal query strings" in {
      QueryParser.parse("key=&&b") must throwA[HttpException]
    }
  }
  
} 