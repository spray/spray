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

package spray.caching

import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import akka.actor.ActorSystem
import spray.util._

class SimpleLruCacheSpec extends Specification with NoTimeConversions {
  implicit val system = ActorSystem()
  import system.dispatcher

  "not cache exceptions" in {
    val cache = new SimpleLruCache[String](10, 10)
    cache(1)((throw new RuntimeException("Naa")): String).await must throwA[RuntimeException]("Naa")
    cache(1)("A").await === "A"
  }

}
