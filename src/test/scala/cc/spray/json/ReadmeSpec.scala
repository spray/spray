package cc.spray.json

import org.specs2.mutable._

class ReadmeSpec extends Specification {

  "The Usage snippets" should {
    "behave as expected" in {
      import DefaultJsonProtocol._
      
      val json = """{ "some": "JSON source" }""" 
      val jsonAst = JsonParser(json)
      jsonAst mustEqual JsObject(JsField("some", "JSON source"))
      
      val json2 = PrettyPrinter(jsonAst)
      json2 mustEqual
              """{
                |  "some": "JSON source"
                |}""".stripMargin
      
      val jsonAst2 = List(1, 2, 3).toJson
      jsonAst2 mustEqual JsArray(JsNumber(1), JsNumber(2), JsNumber(3))
    }
  }
  
  case class Color(name: String, red: Int, green: Int, blue: Int)
  
  "The case class example" should {
    "behave as expected" in {
      object MyJsonProtocol extends DefaultJsonProtocol {
        implicit val colorFormat = jsonFormat(Color, "name", "red", "green", "blue")
      }      
      import MyJsonProtocol._
      
      val json = Color("CadetBlue", 95, 158, 160).toJson
      val color = json.convertTo[Color]
      
      color mustEqual Color("CadetBlue", 95, 158, 160)
    }
  }
  
  "The non case class example" should {
    "behave as expected" in {
      object MyJsonProtocol extends DefaultJsonProtocol {
        implicit object ColorJsonFormat extends JsonFormat[Color] {
          def write(c: Color) = {
            JsArray(JsString(c.name), JsNumber(c.red), JsNumber(c.green), JsNumber(c.blue))
          }
          def read(value: JsValue) = value match {
            case JsArray(JsString(name) :: JsNumber(red) :: JsNumber(green) :: JsNumber(blue) :: Nil) => {
              new Color(name, red.toInt, green.toInt, blue.toInt)
            }
            case _ => throw new DeserializationException("Color expected")
          }
        }
      }      
      import MyJsonProtocol._
      
      val json = Color("CadetBlue", 95, 158, 160).toJson
      val color = json.convertTo[Color]
      
      color mustEqual Color("CadetBlue", 95, 158, 160)
    }
  }
  
}