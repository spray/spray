package cc.spray
package http

import org.specs2.Specification

class HttpRequestSpec extends Specification { def is =

  "The HttpRequest should properly deconstruct" ^
    "simple URIs without query part" ! {
      pathAndQueryParams(HttpRequest(uri = "/ab/c")) mustEqual
        "/ab/c" -> Map.empty
    } ^
    "URIs with a simple query part" ! {
      pathAndQueryParams(HttpRequest(uri = "/?a=b&xyz=123")) mustEqual
        "/" -> Map("a" -> "b", "xyz" -> "123")
    } ^
    "URIs with a query containing encoded elements" ! {
      pathAndQueryParams(HttpRequest(uri = "/squery?Id=3&Eu=/sch/i.html?_animal%3Dcat")) mustEqual
        "/squery" -> Map("Id" -> "3", "Eu" -> "/sch/i.html?_animal=cat")
    }

  def pathAndQueryParams(req: HttpRequest) = req.path -> req.queryParams
}