package docs

import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import spray.routing.{ValidationRejection, HttpService}


class CaseClassExtractionExamplesSpec extends Specification with Specs2RouteTest with HttpService {
  def actorRefFactory = system

  def doSomethingWith(x: Any) = complete(x.toString)

  "example-1" in {
    case class Color(red: Int, green: Int, blue: Int)

    val route =
      path("color") {
        parameters('red.as[Int], 'green.as[Int], 'blue.as[Int]) { (red, green, blue) =>
          val color = Color(red, green, blue)
          doSomethingWith(color) // route working with the Color instance
        }
      }
    Get("/color?red=1&green=2&blue=3") ~> route ~> check { entityAs[String] === "Color(1,2,3)" } // hide
  }

  "example-2" in {
    case class Color(red: Int, green: Int, blue: Int)

    val route =
      path("color") {
        parameters('red.as[Int], 'green.as[Int], 'blue.as[Int]).as(Color) { color =>
          doSomethingWith(color) // route working with the Color instance
        }
      }
    Get("/color?red=1&green=2&blue=3") ~> route ~> check { entityAs[String] === "Color(1,2,3)" } // hide
  }

  "example-3" in {
    case class Color(name: String, red: Int, green: Int, blue: Int)

    val route =
      (path("color" / PathElement) &
        parameters('r.as[Int], 'g.as[Int], 'b.as[Int])).as(Color) { color =>
          doSomethingWith(color) // route working with the Color instance
        }
    Get("/color/abc?r=1&g=2&b=3") ~> route ~> check { entityAs[String] === "Color(abc,1,2,3)" } // hide
  }

  //# example-4
  case class Color(name: String, red: Int, green: Int, blue: Int) {
    require(!name.isEmpty, "color name must not be empty")
    require(0 <= red && red <= 255, "red color component must be between 0 and 255")
    require(0 <= green && green <= 255, "green color component must be between 0 and 255")
    require(0 <= blue && blue <= 255, "blue color component must be between 0 and 255")
  }
  //#

  "example 4 test" in {
    val route =
      (path("color" / PathElement) &
        parameters('r.as[Int], 'g.as[Int], 'b.as[Int])).as(Color) { color =>
          doSomethingWith(color) // route working with the Color instance
        }
    Get("/color/abc?r=1&g=2&b=3") ~> route ~> check {
      entityAs[String] === "Color(abc,1,2,3)"
    }
    Get("/color/abc?r=1&g=2&b=345") ~> route ~> check {
      rejection === ValidationRejection("requirement failed: blue color component must be between 0 and 255")
    }
  }

}
