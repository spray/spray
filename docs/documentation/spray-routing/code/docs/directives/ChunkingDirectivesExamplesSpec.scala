package docs.directives

import spray.http.{HttpResponse, HttpData}

class ChunkingDirectivesExamplesSpec extends DirectivesSpec {
  "autoChunk-0" in {
    val route =
      autoChunk(5) {
        path("long")(complete("This is a long text")) ~
        path("short")(complete("Short"))
      }

    Get("/short") ~> route ~> check {
      responseAs[String] === "Short"
    }
    Get("/long") ~> route ~> check {
      val HttpResponse(_, c0, _, _) = response
      val List(c1, c2, c3) = chunks
      c0.data === HttpData("This ")
      c1.data === HttpData("is a ")
      c2.data === HttpData("long ")
      c3.data === HttpData("text")
    }
  }
  "autoChunkFileBytes" in {
    val route =
      autoChunkFileBytes(5) {
        path("long")(complete("This is a long text"))
      }

    Get("/long") ~> route ~> check {
      // don't chunk long request because it's not from a file
      responseAs[String] === "This is a long text"
    }
  }
}
