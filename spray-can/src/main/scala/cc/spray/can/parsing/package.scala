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

package spray.can


package object parsing {

  private[can] def isTokenChar(c: Char) = c match {
    case x if 'a' <= x && x <= 'z' => true
    case x if 'A' <= x && x <= 'Z' => true
    case '-' => true
    case '(' | ')' | '<' | '>' | '@' | ',' | ';' | ':' | '\\' | '"' | '/' | '[' | ']' | '?' | '=' | '{' | '}' => false
    case x => 32 < x && x < 127
  }

  private[can] def escape(c: Char): String =
    if (Character.isISOControl(c)) String.format("\\u%04x", c: java.lang.Integer)
    else String.valueOf(c)
}