package docs

import org.specs2.mutable.Specification
import spray.http.MediaType

class CustomHttpExtensionExamplesSpec extends Specification {

  "custom-media-type" in {
    import spray.http.MediaTypes._

    val MarkdownType = register(
      MediaType.custom(
        mainType = "text",
        subType = "x-markdown",
        compressible = true,
        binary = false,
        fileExtensions = Seq("markdown", "mdown", "md")))
    getForKey("text" -> "x-markdown") === Some(MarkdownType) // hide
  }

}
