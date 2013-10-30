package docs.directives

import spray.http.StatusCodes.OK
import spray.http.HttpHeaders.Host

class HostDirectivesExamplesSpec extends DirectivesSpec {

  "extract-hostname" in {
    val route =
      hostName { hn =>
        complete(s"Hostname: $hn")
      }

    Get() ~> Host("company.com", 9090) ~> route ~> check {
      status === OK
      responseAs[String] === "Hostname: company.com"
    }
  }

  "list-of-hosts" in {
    val route =
      host("api.company.com", "rest.company.com") {
        complete("Ok")
      }

    Get() ~> Host("rest.company.com") ~> route ~> check {
      status === OK
      responseAs[String] === "Ok"
    }

    Get() ~> Host("notallowed.company.com") ~> route ~> check {
      handled must beFalse
    }
  }

  "predicate" in {
    val shortOnly: String => Boolean = (hostname) => hostname.length < 10

    val route =
      host(shortOnly) {
        complete("Ok")
      }

    Get() ~> Host("short.com") ~> route ~> check {
      status === OK
      responseAs[String] === "Ok"
    }

    Get() ~> Host("verylonghostname.com") ~> route ~> check {
      handled must beFalse
    }
  }

  "using-regex" in {
    val route =
      host("api|rest".r) { prefix =>
        complete(s"Extracted prefix: $prefix")
      } ~
      host("public.(my|your)company.com".r) { captured =>
        complete(s"You came through $captured company")
      }

    Get() ~> Host("api.company.com") ~> route ~> check {
      status === OK
      responseAs[String] === "Extracted prefix: api"
    }

    Get() ~> Host("public.mycompany.com") ~> route ~> check {
      status === OK
      responseAs[String] === "You came through my company"
    }
  }

  "failing-regex" in {
    {
      host("server-([0-9]).company.(com|net|org)".r) { target =>
        complete("Will never complete :'(")
      }
    } must throwA[IllegalArgumentException]
  }

}
