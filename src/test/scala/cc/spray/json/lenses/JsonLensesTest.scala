package cc.spray.json
package lenses

import Predef.{augmentString => _, wrapString => _, _}
import DefaultJsonProtocol._
import cc.spray.json.{JsValue, JsonParser}

object JsonLensesTest extends App {

  import JsonLenses._

  "n" ! set(3)
  "els" ! append {
    "name" ! set("Peter") &&
      "money" ! set(2)
  }
  "els" / element(1) ! update {
    "money" ! set(38) &&
      "name" ! set("Testperson")
  } && "n" ! modify[Int](_ + 1)

  val json = JsonParser("test")
  val newJson = json("els" / "money") = 12

  val i = json.extract[Int]("els" / "money")

  ("els" / element(1) / "money").get[Int] _: (JsValue => Int)

  ("els" / find("money".is[Int](_ < 30)) / "name").get[String]: (JsValue => Option[String])

  ("els" / * / "money").get[Int] _: (JsValue => Seq[Int])
  ("els" / filter("money".is[Int](_ < 30)) / "name").get[String] _: (JsValue => Seq[String])
  "els" / filter("money".is[Int](_ < 30)) / "name" ! modify[String]("Richman " + _)

  //: JsValue => JsValue

  def updateMoney(x: Int) =
    "money" ! modify[Int](_ + x)

  "els" / * ! update(updateMoney(12))
  "els" / * ! extract("name") {
    name: String =>
      updateMoney(name.length)
  }
}
