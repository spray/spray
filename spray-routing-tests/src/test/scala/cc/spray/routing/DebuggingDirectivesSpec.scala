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

import akka.event.LoggingAdapter
import spray.util.EOL


class DebuggingDirectivesSpec extends RoutingSpec {
  sequential

  var debugMsg = ""

  def resetDebugMsg() { debugMsg = "" }

  implicit val log = new LoggingAdapter {
    def isErrorEnabled = true
    def isWarningEnabled = true
    def isInfoEnabled = true
    def isDebugEnabled = true

    def notifyError(message: String) {}
    def notifyError(cause: Throwable, message: String) {}
    def notifyWarning(message: String) {}
    def notifyInfo(message: String) {}
    def notifyDebug(message: String) { debugMsg += message + '\n' }
  }

  "The 'logRequest' directive" should {
    "produce a proper log message for incoming requests" in {
      Get("/hello") ~> {
        logRequest("1") { completeOk }
      } ~> check {
        response === Ok
        debugMsg === "Request 1: HttpRequest(GET, /hello, List(), EmptyEntity, HTTP/1.1)\n"
      }
    }
  }

  "The 'logResponse' directive" should {
    "produce a proper log message for outgoing responses" in {
      resetDebugMsg()
      Get("/hello") ~> {
        logHttpResponse("2") { completeOk }
      } ~> check {
        response === Ok
        debugMsg === "Response 2: HttpResponse(StatusCode(200, OK),EmptyEntity,List(),HTTP/1.1)\n"
      }
    }
  }

  "The 'logRequestResponse' directive" should {
    "produce proper log messages for incoming requests and all outgoing responses" in {
      resetDebugMsg()
      Get("/hello") ~> {
        logRequestResponse("3") { completeOk }
      } ~> check {
        response === Ok
        debugMsg === """|Request 3: HttpRequest(GET, /hello, List(), EmptyEntity, HTTP/1.1)
                        |Completed 3:
                        |  Request: HttpRequest(GET, /hello, List(), EmptyEntity, HTTP/1.1)
                        |  Response: HttpResponse(StatusCode(200, OK),EmptyEntity,List(),HTTP/1.1)
                        |""".stripMargin.replace(EOL, "\n")
      }
    }
  }

}