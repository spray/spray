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

package spray.routing

import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import spray.http.{HttpResponse, StatusCodes}


trait RoutingSpec extends Specification with Directives with Specs2RouteTest {

  val Ok = HttpResponse(StatusCodes.OK)
  val completeOk = complete(Ok)

  def echoComplete[T]: T => Route = { x => complete(x.toString) }

}