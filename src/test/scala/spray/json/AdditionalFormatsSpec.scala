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

package spray.json

import org.specs2.mutable._

class AdditionalFormatsSpec extends Specification {

  case class Foo(id: Long, name: String, foos: Option[List[Foo]] = None)

  object Foo extends AdditionalFormats with ProductFormats {
    implicit val fooFormat: JsonFormat[Foo] = lazyFormat(jsonFormat(Foo.apply, "id", "name", "foos"))
  }

  "The lazyFormat wrapper" should {
    "enable recursive format definitions" in {
      Foo(1, "a", Some(Foo(2, "b", Some(Foo(3, "c") :: Nil)) :: Foo(4, "d") :: Nil)).toJson.toString mustEqual
        """{"id":1,"name":"a","foos":[{"id":2,"name":"b","foos":[{"id":3,"name":"c"}]},{"id":4,"name":"d"}]}"""
    }
  }
}