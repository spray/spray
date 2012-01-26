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
package parsing

import config.HttpParserConfig
import model.{ChunkExtension, HttpHeader}

class TrailerParser(config: HttpParserConfig, extensions: List[ChunkExtension] = Nil, headerCount: Int = 0,
                    headers: List[HttpHeader] = Nil) extends HeaderNameParser(config, null, headerCount, headers) {

  override def valueParser = new HeaderValueParser(config, null, headerCount, headers, headerName.toString) {
    override def nameParser =
      new TrailerParser(config, extensions, headerCount + 1, HttpHeader(headerName, headerValue.toString) :: headers)
  }

  override def headersComplete = ChunkedEndState(extensions, headers)
}