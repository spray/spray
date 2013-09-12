/*
 * Copyright © 2011-2013 the spray project <http://spray.io>
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

package spray.http

import org.specs2.mutable.Specification
import spray.http.parser.CharPredicate

class CharPredicateSpec extends Specification {

  "CharPredicates" should {
    "correctly mask characters" in {
      CharPredicate("4").toString === "CharMask(0010000000000000|0000000000000000)"
      CharPredicate("a").toString === "CharMask(0000000000000000|0000000200000000)"
      show(CharPredicate("&048z{~")) === "&048z{~"
    }
    "support `testAny`" in {
      CharPredicate("abc").matchAny("0125!") must beFalse
      CharPredicate("abc").matchAny("012c5!") must beTrue
    }
    "support `indexOfFirstMatch`" in {
      CharPredicate("abc").indexOfFirstMatch("0125!") === -1
      CharPredicate("abc").indexOfFirstMatch("012c5!") === 3
    }
  }

  def show(pred: CharPredicate): String = {
    val chars: Array[Char] = ('\u0000' to '\u0080').flatMap(c ⇒ if (pred(c)) Some(c) else None)(collection.breakOut)
    new String(chars)
  }

}