/*
 * Copyright (C) 2011 Mathias Doenitz
 * Heavily inspired by the "blueeyes" framework (http://github.com/jdegoes/blueeyes)
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
package http

case class HttpStatus(code: HttpStatusCode, unsafeReason: String = "") {
  val reason = {
    if (unsafeReason.isEmpty)
      code.defaultMessage
    else
      unsafeReason.replace('\r', ' ').replace('\n', ' ')
  }
}

object HttpStatus {
  implicit def statusCode2HttpStatus(code: HttpStatusCode): HttpStatus = HttpStatus(code)
  
  implicit def httpStatus2HttpResponse(status: HttpStatus): HttpResponse = HttpResponse(status)
}