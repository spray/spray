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

package cc.spray.http

import StatusCodes._

/**
 * Sprays immutable model of an HTTP response.
 */
case class HttpResponse(status: HttpStatus = OK,
                        headers: List[HttpHeader] = Nil,
                        content: Option[HttpContent] = None) extends HttpMessage {

  def isSuccess: Boolean = status.code.isInstanceOf[HttpSuccess]
  
  def isWarning: Boolean = status.code.isInstanceOf[HttpWarning]
  
  def isFailure: Boolean = status.code.isInstanceOf[HttpFailure]
  
  def withContentTransformed(f: HttpContent => HttpContent): HttpResponse = copy(content = content.map(f))
}
