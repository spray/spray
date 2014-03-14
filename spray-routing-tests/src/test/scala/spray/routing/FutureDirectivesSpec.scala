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

package spray.routing

import akka.dispatch.{ Promise, Future }
import spray.http.StatusCodes
import spray.util.SingletonException

class FutureDirectivesSpec extends RoutingSpec {

  class TestException(msg: String) extends Exception(msg)
  object TestException extends SingletonException("XXX")
  def throwTestException[T](msgPrefix: String): T ⇒ Nothing = t ⇒ throw new TestException(msgPrefix + t)

  implicit val exceptionHandler = ExceptionHandler {
    case e: TestException ⇒ complete(StatusCodes.InternalServerError, "Oops. " + e)
  }

  "The `onComplete` directive" should {
    "properly unwrap a Future in the success case" in {
      var i = 0
      def nextNumber() = { i += 1; i }
      val route = onComplete(Promise.successful(nextNumber())) { echoComplete }
      Get() ~> route ~> check {
        responseAs[String] === "Right(1)"
      }
      Get() ~> route ~> check {
        responseAs[String] === "Right(2)"
      }
    }
    "properly unwrap a Future in the failure case" in {
      Get() ~> onComplete(Promise.failed[String](new RuntimeException("no"))) { echoComplete } ~> check {
        responseAs[String] === "Left(java.lang.RuntimeException: no)"
      }
    }
    "correct catch exception in the success case" in {
      Get() ~> onComplete(Promise.successful("ok")) { throwTestException("EX when ") } ~> check {
        status === StatusCodes.InternalServerError
        responseAs[String] === "Oops. spray.routing.FutureDirectivesSpec$TestException: EX when Right(ok)"
      }
    }
    "correct catch exception in the failure case" in {
      Get() ~> onComplete(Promise.failed[String](new RuntimeException("no"))) { throwTestException("EX when ") } ~> check {
        status === StatusCodes.InternalServerError
        responseAs[String] === "Oops. spray.routing.FutureDirectivesSpec$TestException: EX when Left(java.lang.RuntimeException: no)"
      }
    }
  }

  "The `onSuccess` directive" should {
    "properly unwrap a Future in the success case" in {
      Get() ~> onSuccess(Promise.successful("yes")) { echoComplete } ~> check {
        responseAs[String] === "yes"
      }
    }
    "throw an exception in the failure case" in {
      Get() ~> onSuccess(Promise.failed[String](TestException)) { echoComplete } ~> check {
        status === StatusCodes.InternalServerError
      }
    }
    "correct catch exception in the success case" in {
      Get() ~> onSuccess(Promise.successful("ok")) { throwTestException("EX when ") } ~> check {
        status === StatusCodes.InternalServerError
        responseAs[String] === "Oops. spray.routing.FutureDirectivesSpec$TestException: EX when ok"
      }
    }
    "correct catch exception in the failure case" in {
      Get() ~> onSuccess(Promise.failed[String](TestException)) { throwTestException("EX when ") } ~> check {
        status === StatusCodes.InternalServerError
        responseAs[String] === "There was an internal server error."
      }
    }
  }

  "The `onFailure` directive" should {
    "properly unwrap a Future in the success case" in {
      Get() ~> onFailure(Promise.successful("yes")) { echoComplete } ~> check {
        responseAs[String] === "yes"
      }
    }
    "throw an exception in the failure case" in {
      Get() ~> onFailure(Promise.failed[String](TestException)) { echoComplete } ~> check {
        responseAs[String] === "spray.routing.FutureDirectivesSpec$TestException$: XXX"
      }
    }
    "correct catch exception in the success case" in {
      Get() ~> onFailure(Promise.successful("ok")) { throwTestException("EX when ") } ~> check {
        status === StatusCodes.OK
        responseAs[String] === "ok"
      }
    }
    "correct catch exception in the failure case" in {
      Get() ~> onFailure(Promise.failed[String](TestException)) { throwTestException("EX when ") } ~> check {
        status === StatusCodes.InternalServerError
        responseAs[String] === "Oops. spray.routing.FutureDirectivesSpec$TestException: EX when spray.routing.FutureDirectivesSpec$TestException$: XXX"
      }
    }
  }
}
