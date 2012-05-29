package cc.spray.json

import org.specs2.mutable.Specification

trait SpecHelpers {
  self: Specification =>

  import JsonLenses._

  def be_json(json: String) =
    be_==(JsonParser(json))

  import org.specs2.matcher.{BeMatching, Matcher}

  override def throwA[E <: Throwable](message: String = ".*")(implicit m: ClassManifest[E]): Matcher[Any] = {
    import java.util.regex.Pattern
    throwA(m).like {
      case e => createExpectable(e.getMessage).applyMatcher(new BeMatching(".*" + Pattern.quote(message) + ".*"))
    }
  }

  case class RichTestString(string: String) {
    def js = JsonParser(string)

    def extract[T: MonadicReader]: Extractor[T] = js.extract[T]

    def extract[T](f: JsValue => T): T = f(js)

    def update(updater: Update): JsValue = updater(js)
  }

  implicit def richTestString(string: String): RichTestString = RichTestString(string)
}
