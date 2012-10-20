package spray.json
package lenses

import org.specs2.mutable.Specification
import spray.json.JsonParser

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
}
