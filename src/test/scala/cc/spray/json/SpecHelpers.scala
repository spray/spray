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

  implicit def richTestString(string: String): RichJsValue = RichJsValue(JsonParser(string))
}
