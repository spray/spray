package cc.spray.json

import Predef.{augmentString => _, wrapString => _, _}
import DefaultJsonProtocol._

object JsonLensesTest extends App {
  import JsonLenses._

  "n" ! set(3)
  "els" ! append {
    "name" ! set("Peter") &&
      "money" ! set(2)
  }
  "els"(1) ! update {
    "money" ! set(38) &&
      "name" ! set("Testperson")
  } && "n" ! updated[Int](_ + 1)

  val json = JsonParser("test")
  val newJson = json("els" / "money") = 12

  val i = json[Int]("els" / "money")

  ("els" andThen element(1) andThen "money").get[Int]: (JsValue => Int)

  ("els" andThen find("money".is[Int](_ < 30)) andThen "name").get[String]: (JsValue => Option[String])

  /*
  ("els" andThen elements andThen "money").get[Int]: (JsValue => Seq[Int])
  ("els" andThen filter("money".is[Int](_ < 30)) andThen "name").get[String]: (JsValue => Seq[String])
  "els" andThen filter("money".is[Int](_ < 30)) andThen "name" ! updated[String]("Richman "+_)//: JsValue => JsValue

  def updateMoney(x: Int) =
    "money" ! updated[Int](_ + x)

  "els" andThen elements ! update(updateMoney(12))
  "els" andThen elements ! extract("name") { name: String =>
    updateMoney(name.length)
  }*/
}
