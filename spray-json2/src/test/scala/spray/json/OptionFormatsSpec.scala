package spray.json

import org.specs2.mutable.Specification

class OptionFormatsSpec extends Specification {
  "The optionFormat" should {
    "convert None to JsNull" in {
      None.asInstanceOf[Option[Int]].toJson mustEqual JsNull
    }
    "convert JsNull to None" in {
      JsNull.as[Option[Int]] mustEqual None
    }
    "convert Some(Hello) to JsString(Hello)" in {
      Some("Hello").asInstanceOf[Option[String]].toJson mustEqual JsString("Hello")
    }
    "convert JsString(Hello) to Some(Hello)" in {
      JsString("Hello").as[Option[String]] mustEqual Some("Hello")
    }
    "convert outer options in nested options to arrays" in {
      def o(v: Option[Option[String]]): Option[Option[String]] = v

      o(Some(Some("test"))).toJson mustEqual JsArray(JsString("test"))
      o(Some(None)).toJson mustEqual JsArray(JsNull)
      o(None).toJson mustEqual JsArray.empty
    }
    "choose innermost option format in nested option formats" in {
      def o(v: Option[Option[String]]): Option[Option[String]] = v

      implicit val other = LeafLevelOptionFormat.asArray

      o(Some(Some("test"))).toJson mustEqual JsArray(JsArray(JsString("test")))
      o(Some(None)).toJson mustEqual JsArray(JsArray.empty)
      o(None).toJson mustEqual JsArray.empty
    }
    "convert outer options in double nested options to arrays" in {
      def o(v: Option[Option[Option[String]]]): Option[Option[Option[String]]] = v

      o(Some(Some(Some("test")))).toJson mustEqual JsArray(JsArray(JsString("test")))
      o(Some(Some(None))).toJson mustEqual JsArray(JsArray(JsNull))
      o(Some(None)).toJson mustEqual JsArray(JsArray.empty)
      o(None).toJson mustEqual JsArray.empty
    }
  }
}
