package docs.directives

import spray.http.FormData

class AnyParamDirectivesExamplesSpec extends DirectivesSpec {
  "example-1" in {
    val route =
      anyParams('name, 'age.as[Int])((name, age) =>
        complete(s"$name is $age years old")
      )

    // extracts query parameters
    Get("/?name=Herman&age=168") ~> route ~> check {
      responseAs[String] === "Herman is 168 years old"
    }

    // extracts form fields
    Post("/", FormData(Seq("name" -> "Herman", "age" -> "168"))) ~> route ~> check {
      responseAs[String] === "Herman is 168 years old"
    }
  }
}
