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
package authentication

import org.specs2.mutable.Specification
import akka.actor.ActorSystem
import util._

class FromConfigUserPassAuthenticatorSpec extends Specification {
  implicit val system = ActorSystem()

  "the FromConfigUserPassAuthenticator" should {
    "extract a BasicUserContext for users defined in the spray config" in {
      FromConfigUserPassAuthenticator().apply(Some("Alice", "banana")).await mustEqual Some(BasicUserContext("Alice"))
    }
  }

  step(system.shutdown())
}