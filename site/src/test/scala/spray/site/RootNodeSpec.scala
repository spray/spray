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

package spray.site

import org.specs2.mutable.Specification


class RootNodeSpec extends Specification {
  skipAll  // comment out to enable

  "The ContentNode" should {
    "correctly load from the Sphinx output" in {
      val out = RootNode.toString
      println(out)
      success
    }
    "properly find a node by uri" in {
      RootNode.find("documentation/spray-io/big-picture/").toString ===
        "Some(Big Picture: documentation/spray-io/big-picture/)"
    }
  }

}
