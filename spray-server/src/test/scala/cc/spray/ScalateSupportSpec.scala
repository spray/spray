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

import http._
import test.AbstractSprayTest

class ScalateSupportSpec extends AbstractSprayTest with ScalateSupport {

  "The ScalateSupport" should {
    "enable the rendering of Scalate templates" in {
      test(HttpRequest()) {
        render("scalate/example.mustache", Map(
          "name" -> "Chris",
          "value" -> 10000,
          "taxed_value" -> (10000 - (10000 * 0.4)),
          "in_ca" -> true
          )
        )
      }.response.content.as[String] mustEqual Right(
        """|Hello Chris
           |You have just won $10,000!
           |Well, $6,000, after taxes.
           |""".stripMargin
      )
    }
  }
  
}