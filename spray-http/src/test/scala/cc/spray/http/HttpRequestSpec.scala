/*
 * Copyright (C) 2011-2012 spray.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package spray.http

import org.specs2.Specification


class HttpRequestSpec extends Specification { def is =

  "The HttpRequest should properly deconstruct" ^
    "simple URIs without query part" ! {
      pathAndQueryParams(HttpRequest(uri = "/ab/c")) === "/ab/c" -> Map.empty
    } ^
    "URIs with a simple query part" ! {
      pathAndQueryParams(HttpRequest(uri = "/?a=b&xyz=123")) === "/" -> Map("a" -> "b", "xyz" -> "123")
    } ^
    "URIs with a query containing encoded elements" ! {
      pathAndQueryParams(HttpRequest(uri = "/squery?Id=3&Eu=/sch/i.html?_animal%3Dcat")) ===
        "/squery" -> Map("Id" -> "3", "Eu" -> "/sch/i.html?_animal=cat")
    }

  def pathAndQueryParams(request: HttpRequest) = {
    val req = request.parseQuery
    req.path -> req.queryParams
  }
}