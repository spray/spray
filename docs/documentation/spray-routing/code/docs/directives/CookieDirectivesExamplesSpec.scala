package docs.directives

import spray.http.HttpHeaders.{ Cookie, `Set-Cookie` }
import spray.http.{DateTime, HttpCookie}
import spray.routing.MissingCookieRejection

class CookieDirectivesExamplesSpec extends DirectivesSpec {
  "cookie" in {
    val route =
      cookie("userName") { nameCookie =>
        complete(s"The logged in user is '${nameCookie.content}'")
      }


    Get("/") ~> Cookie(HttpCookie("userName", "paul")) ~> route ~> check {
      responseAs[String] === "The logged in user is 'paul'"
    }
    // missing cookie
    Get("/") ~> route ~> check {
      rejection === MissingCookieRejection("userName")
    }
    Get("/") ~> sealRoute(route) ~> check {
      responseAs[String] === "Request is missing required cookie 'userName'"
    }
  }
  "optionalCookie" in {
    val route =
      optionalCookie("userName") {
        case Some(nameCookie) => complete(s"The logged in user is '${nameCookie.content}'")
        case None => complete("No user logged in")
      }


    Get("/") ~> Cookie(HttpCookie("userName", "paul")) ~> route ~> check {
      responseAs[String] === "The logged in user is 'paul'"
    }
    Get("/") ~> route ~> check {
      responseAs[String] === "No user logged in"
    }
  }
  "deleteCookie" in {
    val route =
      deleteCookie("userName") {
        complete("The user was logged out")
      }


    Get("/") ~> route ~> check {
      responseAs[String] === "The user was logged out"
      header[`Set-Cookie`] === Some(`Set-Cookie`(HttpCookie("userName", content = "deleted", expires = Some(DateTime.MinValue))))
    }
  }
  "setCookie" in {
    val route =
      setCookie(HttpCookie("userName", content = "paul")) {
        complete("The user was logged in")
      }


    Get("/") ~> route ~> check {
      responseAs[String] === "The user was logged in"
      header[`Set-Cookie`] === Some(`Set-Cookie`(HttpCookie("userName", content = "paul")))
    }
  }
}
