/*
 * Copyright (C) 2011, 2012 Mathias Doenitz
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

package cc.spray.can
package rendering

import java.nio.ByteBuffer
import model._

case class HttpRequestPartRenderingContext(
  requestPart: HttpRequestPart,
  host: String,
  port: Int
)

case class HttpResponsePartRenderingContext(
  responsePart: HttpResponsePart,
  requestMethod: HttpMethod,
  requestProtocol: HttpProtocol,
  requestConnectionHeader: Option[String]
)

case class RenderedMessagePart(buffers: List[ByteBuffer], closeConnection: Boolean = false)