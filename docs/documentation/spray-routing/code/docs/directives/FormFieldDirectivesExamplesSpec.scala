package docs.directives

import spray.http.{FormData, StatusCodes}

class FormFieldDirectivesExamplesSpec extends DirectivesSpec {
  "formFields" in {
    val route =
      formFields('color, 'age.as[Int]) { (color, age) =>
        complete(s"The color is '$color' and the age ten years ago was ${age - 10}")
      }

    Post("/", FormData(Seq("color" -> "blue", "age" -> "68"))) ~> route ~> check {
      responseAs[String] === "The color is 'blue' and the age ten years ago was 58"
    }

    Get("/") ~> sealRoute(route) ~> check {
      status === StatusCodes.BadRequest
      responseAs[String] === "Request is missing required form field 'color'"
    }
  }
}
