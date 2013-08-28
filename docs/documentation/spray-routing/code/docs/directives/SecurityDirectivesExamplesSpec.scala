package docs.directives

import spray.http.{BasicHttpCredentials, HttpChallenge, StatusCodes, HttpHeaders}
import spray.routing.authentication.{BasicAuth, UserPass}
import scala.concurrent.Future
import com.typesafe.config.ConfigFactory

class SecurityDirectivesExamplesSpec extends DirectivesSpec {
  "authenticate-custom-user-pass-authenticator" in {
    def myUserPassAuthenticator(userPass: Option[UserPass]): Future[Option[String]] =
      Future {
        if (userPass.exists(up => up.user == "John" && up.pass == "p4ssw0rd")) Some("John")
        else None
      }

    val route =
      sealRoute {
        path("secured") {
          authenticate(BasicAuth(myUserPassAuthenticator _, realm = "secure site")) { userName =>
            complete(s"The user is '$userName'")
          }
        }
      }

    Get("/secured") ~> route ~> check {
      status === StatusCodes.Unauthorized
      entityAs[String] === "The resource requires authentication, which was not supplied with the request"
      header[HttpHeaders.`WWW-Authenticate`].get.challenges.head === HttpChallenge("Basic", "secure site")
    }

    val validCredentials = BasicHttpCredentials("John", "p4ssw0rd")
    Get("/secured") ~>
      addCredentials(validCredentials) ~> // adds Authorization header
      route ~> check {
      entityAs[String] === "The user is 'John'"
    }

    val invalidCredentials = BasicHttpCredentials("Peter", "pan")
    Get("/secured") ~>
      addCredentials(invalidCredentials) ~>  // adds Authorization header
      route ~> check {
        status === StatusCodes.Unauthorized
        entityAs[String] === "The supplied authentication is invalid"
        header[HttpHeaders.`WWW-Authenticate`].get.challenges.head === HttpChallenge("Basic", "secure site")
      }
  }

  "authenticate-from-config" in {
    def extractUser(userPass: UserPass): String = userPass.user
    val config = ConfigFactory.parseString("John = p4ssw0rd")

    val route =
      sealRoute {
        path("secured") {
          authenticate(BasicAuth(realm = "secure site", config = config, createUser = extractUser _)) { userName =>
            complete(s"The user is '$userName'")
          }
        }
      }

    Get("/secured") ~> route ~> check {
      status === StatusCodes.Unauthorized
      entityAs[String] === "The resource requires authentication, which was not supplied with the request"
      header[HttpHeaders.`WWW-Authenticate`].get.challenges.head === HttpChallenge("Basic", "secure site")
    }

    val validCredentials = BasicHttpCredentials("John", "p4ssw0rd")
    Get("/secured") ~>
      addCredentials(validCredentials) ~>  // adds Authorization header
      route ~> check {
      entityAs[String] === "The user is 'John'"
    }

    val invalidCredentials = BasicHttpCredentials("Peter", "pan")
    Get("/secured") ~>
      addCredentials(invalidCredentials) ~>  // adds Authorization header
      route ~> check {
        status === StatusCodes.Unauthorized
        entityAs[String] === "The supplied authentication is invalid"
        header[HttpHeaders.`WWW-Authenticate`].get.challenges.head === HttpChallenge("Basic", "secure site")
      }
  }

  "authorize-1" in {
    def extractUser(userPass: UserPass): String = userPass.user
    val config = ConfigFactory.parseString("John = p4ssw0rd\nPeter = pan")
    def hasPermissionToPetersLair(userName: String) = userName == "Peter"

    val route =
      sealRoute {
        authenticate(BasicAuth(realm = "secure site", config = config, createUser = extractUser _)) { userName =>
          path("peters-lair") {
            authorize(hasPermissionToPetersLair(userName)) {
              complete(s"'$userName' visited Peter's lair")
            }
          }
        }
      }

    val johnsCred = BasicHttpCredentials("John", "p4ssw0rd")
    Get("/peters-lair") ~>
      addCredentials(johnsCred) ~>  // adds Authorization header
      route ~> check {
        status === StatusCodes.Forbidden
        entityAs[String] === "The supplied authentication is not authorized to access this resource"
      }

    val petersCred = BasicHttpCredentials("Peter", "pan")
    Get("/peters-lair") ~>
      addCredentials(petersCred) ~>  // adds Authorization header
      route ~> check {
        entityAs[String] === "'Peter' visited Peter's lair"
      }
  }
}
