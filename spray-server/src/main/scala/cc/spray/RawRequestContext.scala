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

import java.io.{InputStream, OutputStream}

case class RawRequestContext(request: RawRequest, complete: (RawResponse => Unit) => Unit)

trait RawRequest {
  def method: String
  def uri: String
  def headers: collection.Map[String, String]
  def inputStream: InputStream
  def remoteIP: String
  def protocol: String
}

trait RawResponse {
  def setStatus(code: Int)
  def addHeader(name: String, value: String)
  def outputStream: OutputStream
}