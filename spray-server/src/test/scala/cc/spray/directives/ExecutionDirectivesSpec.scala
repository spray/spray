/*
* Copyright (C) 2011-2012 spray.cc
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

package cc.spray
package directives

import test.AbstractSprayTest
import http.HttpRequest

class ExecutionDirectivesSpec extends AbstractSprayTest {

  "the 'dynamic' directive" should {
    "cause its inner route to be revaluated for every request anew" in {
      var a = ""
      val staticRoute = get { a += "x"; completeWith(a) }
      val dynamicRoute = get { dynamic { a += "x"; completeWith(a) } }
      def expect(route: Route, s: String) =
        test(HttpRequest())(route).response.content.as[String] mustEqual Right(s)
      expect(staticRoute, "x")
      expect(staticRoute, "x")
      expect(staticRoute, "x")
      expect(dynamicRoute, "xx")
      expect(dynamicRoute, "xxx")
      expect(dynamicRoute, "xxxx")
    }
  }

}