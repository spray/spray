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

import akka.dispatch.{ Promise, Future }
import spray.http.StatusCodes
import spray.util.SingletonException

class FutureDirectivesSpec extends RoutingSpec {

  object TestException extends SingletonException

  "The `onComplete` directive" should {
    "properly unwrap a Future in the success case" in {
      Get() ~> onComplete(Promise.successful("yes")) { echoComplete } ~> check {
        entityAs[String] === "Right(yes)"
      }
    }
    "properly unwrap a Future in the failure case" in {
      Get() ~> onComplete(Promise.failed[String](new RuntimeException("no"))) { echoComplete } ~> check {
        entityAs[String] === "Left(java.lang.RuntimeException: no)"
      }
    }
  }

  "The `onSuccess` directive" should {
    "properly unwrap a Future in the success case" in {
      Get() ~> onSuccess(Promise.successful("yes")) { echoComplete } ~> check {
        entityAs[String] === "yes"
      }
    }
    "throw an exception in the failure case" in {
      Get() ~> onSuccess(Promise.failed[String](TestException)) { echoComplete } ~> check {
        status === StatusCodes.InternalServerError
      }
    }
  }

  "The `onFailure` directive" should {
    "properly unwrap a Future in the success case" in {
      Get() ~> onFailure(Promise.successful("yes")) { echoComplete } ~> check {
        entityAs[String] === "yes"
      }
    }
    "throw an exception in the failure case" in {
      Get() ~> onFailure(Promise.failed[String](TestException)) { echoComplete } ~> check {
        entityAs[String] === "spray.routing.FutureDirectivesSpec$TestException$"
      }
    }
  }

}
