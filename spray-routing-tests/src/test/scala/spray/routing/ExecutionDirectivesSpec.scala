/*
 * Copyright (C) 2011-2013 spray.io
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

package spray.routing

class ExecutionDirectivesSpec extends RoutingSpec {

  "the 'dynamicIf' directive" should {
    "cause its inner route to be revaluated for every request anew, if enabled" in {
      var a = ""
      val staticRoute = get { dynamicIf(enabled = false) { a += "x"; complete(a) } }
      val dynamicRoute = get { dynamic { a += "x"; complete(a) } }
      def expect(route: Route, s: String) = Get() ~> route ~> check { entityAs[String] === s }
      expect(staticRoute, "x")
      expect(staticRoute, "x")
      expect(staticRoute, "x")
      expect(dynamicRoute, "xx")
      expect(dynamicRoute, "xxx")
      expect(dynamicRoute, "xxxx")
    }
  }
}