package docs.directives

import spray.http.HttpHeaders.`Remote-Address`

class MiscDirectivesExamplesSpec extends DirectivesSpec {
  "clientIP-example" in {
    val route = clientIP { ip =>
      complete(s"Client's ip is '${ip.ip.getHostAddress}'")
    }

    Get("/").withHeaders(`Remote-Address`("192.168.3.12")) ~> route ~> check {
      responseAs[String] === "Client's ip is '192.168.3.12'"
    }
  }
}
