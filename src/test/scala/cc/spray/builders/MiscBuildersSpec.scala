/*
 * Copyright (C) 2011 Mathias Doenitz
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
package builders

import org.specs.Specification
import http._
import HttpStatusCodes._
import HttpHeaders._
import test.SprayTest

class MiscBuildersSpec extends Specification with SprayTest with ServiceBuilder {

  val Ok = HttpResponse()
  val completeOk: Route = { _.complete(Ok) }
  
  "respondsWithStatus" should {
    "set the given status on successful responses" in {
      test(HttpRequest()) { 
        respondsWithStatus(Created) { completeOk }
      }.response mustEqual HttpResponse(Created) 
    }
    "leave rejections unaffected" in {
      test(HttpRequest()) { 
        respondsWithStatus(Created) { _.reject() }
      }.rejections mustEqual Set() 
    }
  }
  
  "respondsWithHeader" should {
    "add the given headers to successful responses" in {
      test(HttpRequest()) { 
        respondsWithHeader(CustomHeader("custom", "custom")) { completeOk }
      }.response mustEqual HttpResponse(headers = CustomHeader("custom", "custom") :: Nil) 
    }
    "leave rejections unaffected" in {
      test(HttpRequest()) { 
        respondsWithHeader(CustomHeader("custom", "custom")) { _.reject() }
      }.rejections mustEqual Set() 
    }
  }

}