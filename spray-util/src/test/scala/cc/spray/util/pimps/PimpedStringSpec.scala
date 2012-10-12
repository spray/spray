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

package spray.util
package pimps

import org.specs2.mutable.Specification

class PimpedStringSpec extends Specification {

  "fastSplit" should {
    "properly split a string with three fields" in {
      "abc/def/gh".fastSplit('/') === List("abc", "def", "gh")
    }
    "return a single-element list containing the original string if the string does not contain the delimiter" in {
      "abcdefgh".fastSplit('/') === List("abcdefgh")
    }
    "return a single empty string for the empty string" in {
      "".fastSplit('/') === List("")
    }
    "include a leading empty string if the underlying string starts with a delimiter" in {
      "/abc".fastSplit('/') === List("", "abc")
    }
    "include a trailing empty string if the underlying string ends with a delimiter" in {
      "abc/".fastSplit('/') === List("abc", "")
    }
  }

  "lazySplit" should {
    "properly split a string with three fields" in {
      "abc/def/gh".lazySplit('/') === Stream("abc", "def", "gh")
    }
    "return a single-element list containing the original string if the string does not contain the delimiter" in {
      "abcdefgh".lazySplit('/') === Stream("abcdefgh")
    }
    "return a single empty string for the empty string" in {
      "".lazySplit('/') === Stream("")
    }
    "include a leading empty string if the underlying string starts with a delimiter" in {
      "/abc".lazySplit('/') === Stream("", "abc")
    }
    "include a trailing empty string if the underlying string ends with a delimiter" in {
      "abc/".lazySplit('/') === Stream("abc", "")
    }
  }

  "getAsciiBytes" should {
    "perform a simple ASCII encoding of the string" in {
       "Hello there".getAsciiBytes === Array(72, 101, 108, 108, 111, 32, 116, 104, 101, 114, 101)
    }
  }

  "secure_==" should {
    "properly compare two strings for equality" in {
      ("foo" secure_== "foo") must beTrue
      ("FoO" secure_== "FoO") must beTrue
      ("" secure_== "") must beTrue

      ("FoO" secure_== "Foo") must beFalse
      ("FoO" secure_== "") must beFalse
      ("FoO" secure_== "FoObar") must beFalse
    }
  }
}