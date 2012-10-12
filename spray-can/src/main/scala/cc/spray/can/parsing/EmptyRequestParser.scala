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

package spray.can.parsing


class EmptyRequestParser(settings: ParserSettings) extends CharacterParser {
  import spray.http.HttpMethods._

  def handleChar(cursor: Char) = cursor match {
    case 'G' => new MethodParser(settings, GET)
    case 'P' => new CharacterParser {
      override def handleChar(cursor: Char) = cursor match {
        case 'O' => new MethodParser(settings, POST, 1)
        case 'U' => new MethodParser(settings, PUT, 1)
        case 'A' => new MethodParser(settings, PATCH, 1)
        case _ => badMethod
      }
    }
    case 'D' => new MethodParser(settings, DELETE)
    case 'H' => new MethodParser(settings, HEAD)
    case 'O' => new MethodParser(settings, OPTIONS)
    case 'T' => new MethodParser(settings, TRACE)
    case _ => badMethod
  }
}