/*
 * Copyright (C) 2011 Mathias Doenitz
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team (http://github.com/jdegoes/blueeyes)
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


case class HttpStatus(code: StatusCode, reason: String = "") {
  def nonEmptyReason = if (reason.isEmpty) code.defaultMessage else reason
}

object HttpStatus {
  implicit def statusCode2HttpStatus(code: StatusCode): HttpStatus = HttpStatus(code)
  
  implicit def httpStatus2HttpResponse(status: HttpStatus): HttpResponse = HttpResponse(status)
}