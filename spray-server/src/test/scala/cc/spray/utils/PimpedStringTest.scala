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
package utils

import org.specs2.mutable.Specification

class PimpedStringTest extends Specification {

  "fastSplit" should {
    "properly split a string with three elements" in {
      "abc/def/gh".fastSplit('/') mustEqual List("abc", "def", "gh")
    }
    "return a single-element list containing the original string if the string does not contain the delimiter" in {
      "abcdefgh".fastSplit('/') mustEqual List("abcdefgh")
    }
    "return a single empty string for the empty string" in {
      "".fastSplit('/') mustEqual List("")
    }
    "include a leading empty string if the underlying string starts with a delimiter" in {
      "/abc".fastSplit('/') mustEqual List("", "abc")
    }
    "include a trailing empty string if the underlying string ends with a delimiter" in {
      "abc/".fastSplit('/') mustEqual List("abc", "")
    }
  }

  "lazySplit" should {
    "properly split a string with three elements" in {
      "abc/def/gh".lazySplit('/') mustEqual Stream("abc", "def", "gh")
    }
    "return a single-element list containing the original string if the string does not contain the delimiter" in {
      "abcdefgh".lazySplit('/') mustEqual Stream("abcdefgh")
    }
    "return a single empty string for the empty string" in {
      "".lazySplit('/') mustEqual Stream("")
    }
    "include a leading empty string if the underlying string starts with a delimiter" in {
      "/abc".lazySplit('/') mustEqual Stream("", "abc")
    }
    "include a trailing empty string if the underlying string ends with a delimiter" in {
      "abc/".lazySplit('/') mustEqual Stream("abc", "")
    }
  }

}