/*
 * Copyright © 2011-2013 the spray project <http://spray.io>
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

import org.specs2.mutable.Specification

class RenderingSpec extends Specification {

  "The StringRendering" should {
    "correctly render Ints and Longs to decimal" in {
      (new StringRendering ~~ 0).get === "0"
      (new StringRendering ~~ 123456789).get === "123456789"
      (new StringRendering ~~ -123456789L).get === "-123456789"
    }
    "correctly render Ints and Longs to hex" in {
      (new StringRendering ~~% 0).get === "0"
      (new StringRendering ~~% 65535).get === "ffff"
      (new StringRendering ~~% 65537).get === "10001"
      (new StringRendering ~~% -10L).get === "fffffffffffffff6"
    }
    "correctly render plain Strings" in {
      (new StringRendering ~~ "").get === ""
      (new StringRendering ~~ "hello").get === "hello"
    }
    "correctly render escaped Strings" in {
      (new StringRendering ~~# "").get === ""
      (new StringRendering ~~# "hello").get === "hello"
      (new StringRendering ~~# """hel"lo""").get === """"hel\"lo""""
    }
  }
}