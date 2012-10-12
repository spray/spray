package docs

import org.specs2.mutable.Specification


class CustomHttpExtensionExamplesSpec extends Specification {

  "custom-media-type" in {
    import spray.http.MediaTypes._
    val MarkdownType = register(CustomMediaType("text/x-markdown", "markdown", "mdown", "md"))
    getForKey("text" -> "x-markdown") === Some(MarkdownType) // hide
  }

}
