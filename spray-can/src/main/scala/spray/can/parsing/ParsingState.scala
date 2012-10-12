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

import annotation.tailrec
import java.nio.ByteBuffer
import spray.http.StatusCodes.NotImplemented


/**
 * A ParsingState instance holds the complete parsing state at any particular point in the request or response
 * parsing process.
 */
trait ParsingState

/**
 * A ParsingState holding an intermediate parsing state, i.e. which does not represent a complete parsing result
 */
trait IntermediateState extends ParsingState {
  def read(buf: ByteBuffer): ParsingState
}

/**
 * An IntermediateState working on US-ASCII encoded characters (e.g. the HTTP messages header section)
 */
abstract class CharacterParser extends IntermediateState {

  def read(buf: ByteBuffer): ParsingState = {
    @tailrec
    def read(parser: ParsingState): ParsingState = parser match {
      case x: CharacterParser =>
        if (buf.remaining > 0) {
          val cursor = buf.get.asInstanceOf[Char] // simple US-ASCII encoding conversion
          read(x.handleChar(cursor))
        } else x
      case x: IntermediateState => x.read(buf) // a body parser
      case x: FinalParsingState => x
    }
    read(this)
  }

  def handleChar(cursor: Char): ParsingState

  def badMethod = ErrorState(NotImplemented)
}