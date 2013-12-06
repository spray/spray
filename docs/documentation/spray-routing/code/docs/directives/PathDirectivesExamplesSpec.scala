package docs.directives

import spray.routing.PathMatcher1
import spray.testkit.NoAutoHtmlLinkFragments

class PathDirectivesExamplesSpec extends DirectivesSpec with NoAutoHtmlLinkFragments {

  //# path-matcher
  val matcher: PathMatcher1[Option[Int]] =
    "foo" / "bar" / "X" ~ IntNumber.? / ("edit" | "create")
  //#

  //# path-dsl
  // matches /foo/
  path("foo" /)

  // matches e.g. /foo/123 and extracts "123" as a String
  path("foo" / """\d+""".r)

  // matches e.g. /foo/bar123 and extracts "123" as a String
  path("foo" / """bar(\d+)""".r)

  // identical to `path(Segments)`
  path(Segment.repeat(separator = Slash))

  // matches e.g. /i42 or /hCAFE and extracts an Int
  path("i" ~ IntNumber | "h" ~ HexIntNumber)

  // identical to path("foo" ~ (PathEnd | Slash))
  path("foo" ~ Slash.?)

  // matches /red or /green or /blue and extracts 1, 2 or 3 respectively
  path(Map("red" -> 1, "green" -> 2, "blue" -> 3))

  // matches anything starting with "/foo" except for /foobar
  pathPrefix("foo" ~ !"bar")
  //#

  //# pathPrefixTest-, rawPathPrefix-, rawPathPrefixTest-, pathSuffix-, pathSuffixTest-
  val completeWithUnmatchedPath =
    unmatchedPath { p =>
      complete(p.toString)
    }

  //#

  "path-example" in {
    val route =
      path("foo") {
        complete("/foo")
      } ~
      path("foo" / "bar") {
        complete("/foo/bar")
      } ~
      pathPrefix("ball") {
        pathEnd {
          complete("/ball")
        } ~
        path(IntNumber) { int =>
          complete(if (int % 2 == 0) "even ball" else "odd ball")
        }
      }

    Get("/") ~> route ~> check {
      handled === false
    }

    Get("/foo") ~> route ~> check {
      responseAs[String] === "/foo"
    }

    Get("/foo/bar") ~> route ~> check {
      responseAs[String] === "/foo/bar"
    }

    Get("/ball/1337") ~> route ~> check {
      responseAs[String] === "odd ball"
    }
  }

  "pathEnd-" in {
    val route =
      pathPrefix("foo") {
        pathEnd {
          complete("/foo")
        } ~
        path("bar") {
          complete("/foo/bar")
        }
      }

    Get("/foo") ~> route ~> check {
      responseAs[String] === "/foo"
    }

    Get("/foo/") ~> route ~> check {
      handled === false
    }

    Get("/foo/bar") ~> route ~> check {
      responseAs[String] === "/foo/bar"
    }
  }

  "pathEndOrSingleSlash-" in {
    val route =
      pathPrefix("foo") {
        pathEndOrSingleSlash {
          complete("/foo")
        } ~
        path("bar") {
          complete("/foo/bar")
        }
      }

    Get("/foo") ~> route ~> check {
      responseAs[String] === "/foo"
    }

    Get("/foo/") ~> route ~> check {
      responseAs[String] === "/foo"
    }

    Get("/foo/bar") ~> route ~> check {
      responseAs[String] === "/foo/bar"
    }
  }

  "pathPrefix-" in {
    val route =
      pathPrefix("ball") {
        pathEnd {
          complete("/ball")
        } ~
        path(IntNumber) { int =>
          complete(if (int % 2 == 0) "even ball" else "odd ball")
        }
      }

    Get("/") ~> route ~> check {
      handled === false
    }

    Get("/ball") ~> route ~> check {
      responseAs[String] === "/ball"
    }

    Get("/ball/1337") ~> route ~> check {
      responseAs[String] === "odd ball"
    }
  }

  "pathPrefixTest-" in {
    val route =
      pathPrefixTest("foo" | "bar") {
        pathPrefix("foo") { completeWithUnmatchedPath } ~
        pathPrefix("bar") { completeWithUnmatchedPath }
      }

    Get("/foo/doo") ~> route ~> check {
      responseAs[String] === "/doo"
    }

    Get("/bar/yes") ~> route ~> check {
      responseAs[String] === "/yes"
    }
  }

  "pathSingleSlash-" in {
    val route =
      pathSingleSlash {
        complete("root")
      } ~
      pathPrefix("ball") {
        pathSingleSlash {
          complete("/ball/")
        } ~
        path(IntNumber) { int =>
          complete(if (int % 2 == 0) "even ball" else "odd ball")
        }
      }

    Get("/") ~> route ~> check {
      responseAs[String] === "root"
    }

    Get("/ball") ~> route ~> check {
      handled === false
    }

    Get("/ball/") ~> route ~> check {
      responseAs[String] === "/ball/"
    }

    Get("/ball/1337") ~> route ~> check {
      responseAs[String] === "odd ball"
    }
  }

  "pathSuffix-" in {
    val route =
      pathPrefix("start") {
        pathSuffix("end") {
          completeWithUnmatchedPath
        } ~
        pathSuffix("foo" / "bar" ~ "baz") {
          completeWithUnmatchedPath
        }
      }

    Get("/start/middle/end") ~> route ~> check {
      responseAs[String] === "/middle/"
    }

    Get("/start/something/barbaz/foo") ~> route ~> check {
      responseAs[String] === "/something/"
    }
  }

  "pathSuffixTest-" in {
    val route =
      pathSuffixTest(Slash) {
        complete("slashed")
      } ~
      complete("unslashed")

    Get("/foo/") ~> route ~> check {
      responseAs[String] === "slashed"
    }
    Get("/foo") ~> route ~> check {
      responseAs[String] === "unslashed"
    }
  }

  "rawPathPrefix-" in {
    val route =
      pathPrefix("foo") {
        rawPathPrefix("bar") { completeWithUnmatchedPath } ~
        rawPathPrefix("doo") { completeWithUnmatchedPath }
      }

    Get("/foobar/baz") ~> route ~> check {
      responseAs[String] === "/baz"
    }

    Get("/foodoo/baz") ~> route ~> check {
      responseAs[String] === "/baz"
    }
  }

  "rawPathPrefixTest-" in {
    val route =
      pathPrefix("foo") {
        rawPathPrefixTest("bar") {
          completeWithUnmatchedPath
        }
      }

    Get("/foobar") ~> route ~> check {
      responseAs[String] === "bar"
    }

    Get("/foobaz") ~> route ~> check {
      handled === false
    }
  }
}
