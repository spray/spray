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
import java.util.concurrent.atomic.AtomicInteger
import akka.dispatch.{Future, AlreadyCompletedFuture}

class CachingAuthenticatorSpec extends Specification {

  class CountingAuthenticator extends UserPassAuthenticator[Int] {
    val counter = new AtomicInteger
    def apply(userPass: Option[(String, String)]): Future[Option[Int]] =
      new AlreadyCompletedFuture(Right(Some(counter.incrementAndGet())))
  }

  val CountingAuthenticator = new CountingAuthenticator
  val CachinggAuthenticator = new CountingAuthenticator with AuthenticationCaching[Int]

  "the AuthenticationCaching" should {
    "cache the auth results from the underlying UserPassAuthenticator" in {
      val dummyCreds = Some("", "")
      CountingAuthenticator(dummyCreds).get mustEqual Some(1)
      CountingAuthenticator(dummyCreds).get mustEqual Some(2)
      CountingAuthenticator(dummyCreds).get mustEqual Some(3)
      CachinggAuthenticator(dummyCreds).get mustEqual Some(1)
      CachinggAuthenticator(dummyCreds).get mustEqual Some(1)
      CachinggAuthenticator(dummyCreds).get mustEqual Some(1)
    }
  }

}