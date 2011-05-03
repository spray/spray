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

import org.parboiled.common.FileUtils
import java.io.ByteArrayInputStream

object TimeOutHandler {
  
  type Handler = (RawRequest, RawResponse) => Unit
  
  val DefaultHandler: Handler = { (_, response) =>
    val bytes = "The server could not handle the request in the appropriate time frame (async timeout)".getBytes("ISO-8859-1")
    response.setStatus(500)
    response.addHeader("Async-Timeout", "expired")
    response.addHeader("Content-Type", "text/plain")
    response.addHeader("Content-Length", bytes.length.toString)
    if (SpraySettings.CloseConnection) response.addHeader("Connection","close")
    FileUtils.copyAll(new ByteArrayInputStream(bytes), response.outputStream)
  } 
  
  private var handler: Handler = _
  
  def get = if (handler == null) DefaultHandler else handler
  
  def set(handler: Handler) { this.handler = handler }
}