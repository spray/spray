package cc.spray.json

import org.specs2.Specification

/**
 * Based on the tests provided with LiquidJson:
 * https://github.com/mighdoll/liquidJson
 */
class DynamicJsonSpec extends Specification {
  val arrayJson   = """ { "array": [3,1,4,5] } """.asJson
  val numberJson  = """ { "number": 9, "float": 1.5 } """.asJson
  val stringJson  = """ { "name": "lee", "surname": "mighty" } """.asJson
  val parentJson  = """ { "parent": { "childNumber":2 } } """.asJson
  val charJson    = """ { "initial": "j" } """.asJson

  def is =
  "spray-json should" ^
    "be able to properly extract" ^
      "a simple numeric property"           ! { numberJson.number.as[Int] === 9 } ^
      "an optional numeric property"        ! { numberJson.number.as[Option[Int]] === Some(9) } ^
      "a nested numeric field"              ! { parentJson.parent.childNumber.as[Int] === 2 } ^
      "a simple string"                     ! { stringJson.name.as[String] === "lee" } ^
      "an optional string"                  ! { stringJson.name.as[Option[String]] === Some("lee") } ^
      "an array via apply"                  ! { arrayJson.array.apply(2).as[Int] === 4 } ^
      "an array via applyDymamic"           ! { arrayJson.array(3).as[Int] === 5 } ^
      "an optional array via applyDymamic"  ! { arrayJson.array(1).as[Option[Int]] === Some(1) } ^
      "a char"                              ! { charJson.initial.as[Char] === 'j' } ^
      "a left Either"                       ! { charJson.initial.as[Either[Char, Int]] === Left('j') } ^
      "a right Either"                      ! { stringJson.name.as[Either[Char, String]] === Right("lee") } ^
      "a Double"                            ! { numberJson.float.as[Double] === 1.5 } ^
      "a List[Long]"                        ! { arrayJson.array.as[List[Long]] === List(3L, 1L, 4L, 5L) } ^
      p^
    "support monadic methods" ^
      "map"               ! { stringJson.name.map((name :String) => name) === Success("lee") } ^
      "map (for)"         ! { (for (name :String <- stringJson.name) yield name) === Success("lee") } ^
      "flatMap"           ! { stringJson.name.flatMap((name :String) => Success(name)) === Success("lee") } ^
      "flatMap (for)"     ! {
        { for {
            name :String <- stringJson.name
            surname :String <- stringJson.surname
          } yield name + " " + surname
        } === Success("lee mighty")
      } ^
      "foreach"           ! {
        var found = ""
        stringJson.name foreach { found = _ :String }
        found === "lee"
      } ^
      "foreach (for)" ! {
        var found = false
        for (name :String <- stringJson.name) if (name == "lee") found = true
        found must beTrue
      } ^
      p^
    "raise exceptions for" ^
      "illegal String to Int conversions" ! {
        stringJson.name.as[Option[Int]] must throwA[DeserializationException]("Expected Int as JsNumber, but got \"lee\"")
      } ^
      "out-of-bounds array access" ! {
        arrayJson.array(5).as[Int] must throwAn[IndexOutOfBoundsException]("5")
      } ^
      "illegal Either creation" ! {
        arrayJson.array(0).as[Either[Char, String]] must throwA[DeserializationException](
          "Could not read Either value:\n" +
          "cc.spray.json.DeserializationException: Expected Char as single-character JsString, but got 3\n" +
          "---------- and ----------\n" +
          "cc.spray.json.DeserializationException: Expected String as JsString, but got 3"
        )
      }

}