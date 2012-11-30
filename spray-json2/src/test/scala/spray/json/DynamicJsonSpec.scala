package spray.json

import org.specs2.Specification

/**
 * Based on the tests provided with LiquidJson:
 * https://github.com/mighdoll/liquidJson
 */
class DynamicJsonSpec extends Specification {
  val arrayJson   = """ { "array": [3,1,4,5] } """.asJson.dyn
  val numberJson  = """ { "number": 9, "float": 1.5 } """.asJson.dyn
  val stringJson  = """ { "name": "lee", "surname": "mighty" } """.asJson.dyn
  val parentJson  = """ { "parent": { "childNumber":2 } } """.asJson.dyn
  val charJson    = """ { "initial": "j" } """.asJson.dyn

  def is =
  "spray-json should" ^
    "be able to properly extract" ^
      "a simple numeric property"           ! { numberJson.number.get[Int] === 9 } ^
      "an optional numeric property"        ! { numberJson.number.get[Option[Int]] === Some(9) } ^
      "a nested numeric field"              ! { parentJson.parent.childNumber.get[Int] === 2 } ^
      "a simple string"                     ! { stringJson.name.get[String] === "lee" } ^
      "an optional string"                  ! { stringJson.name.get[Option[String]] === Some("lee") } ^
      "an array via apply"                  ! { arrayJson.array(2).get[Int] === 4 } ^
      "an array via applyDymamic"           ! { arrayJson.array(3).get[Int] === 5 } ^
      "an optional array via applyDymamic"  ! { arrayJson.array(1).get[Option[Int]] === Some(1) } ^
      "a char"                              ! { charJson.initial.get[Char] === 'j' } ^
      "a left Either"                       ! { charJson.initial.get[Either[Char, Int]] === Left('j') } ^
      "a right Either"                      ! { stringJson.name.get[Either[Char, String]] === Right("lee") } ^
      "a Double"                            ! { numberJson.float.get[Double] === 1.5 } ^
      "a List[Long]"                        ! { arrayJson.array.get[List[Long]] === List(3L, 1L, 4L, 5L) } ^
      p^
    "support monadic methods" ^
      "map"               ! { stringJson.name.as[String].map(name => name) === Success("lee") } ^
      "map (for)"         ! {
        (for (name <- stringJson.name.as[String]) yield name) === Success("lee")
      } ^
      "flatMap"           ! { stringJson.name.as[String].flatMap(name => Success(name)) === Success("lee") } ^
      "flatMap (for)"     ! {
        { for {
            name <- stringJson.name.as[String]
            surname <- stringJson.surname.as[String]
          } yield name + " " + surname
        } === Success("lee mighty")
      } ^
      "foreach"           ! {
        var found = ""
        stringJson.name.as[String] foreach { found = _ }
        found === "lee"
      } ^
      "foreach (for)" ! {
        var found = false
        for (name <- stringJson.name.as[String]) if (name == "lee") found = true
        found must beTrue
      } ^
      "flatMap from parent" ! {
        (for {
          parentInfo <- parentJson.parent
          count      <- parentInfo.childNumber.as[Int]
        } yield count) === Success(2)
      } ^
      "foreach from parent" ! {
        var found = false
        for {
          parentInfo <- parentJson.parent
          count      <- parentInfo.childNumber.as[Int]
        } if (count == 2) found = true
        found must beTrue
      } ^
      "filter from parent" ! {
        (for {
          parentInfo <- parentJson.parent if parentInfo.as[JsObject].fields.size == 1
          count      <- parentInfo.childNumber.as[Int]
        } yield count) === Success(2)
      } ^
      p^
    "raise exceptions for" ^
      "illegal String to Int conversions" ! {
        stringJson.name.get[Option[Int]] must throwA[DeserializationException]("Expected int as JsNumber, but got \"lee\"")
      } ^
      "out-of-bounds array access" ! {
        arrayJson.array(5).get[Int] must throwAn[IndexOutOfBoundsException]("5")
      } ^
      "illegal Either creation" ! {
        arrayJson.array(0).get[Either[Char, String]] must throwA[DeserializationException](
          "Could not read Either value:\n" +
          "spray.json.DeserializationException: Expected Char as single-character JsString, but got 3\n" +
          "---------- and ----------\n" +
          "spray.json.DeserializationException: Expected String as JsString, but got 3"
        )
      }

}
