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

import org.specs.Specification
import java.util.{TimeZone, GregorianCalendar}

class Rfc1123Spec extends Specification {
  
  "Rfc1123" should {
    "format a 2011-04-28 T12:56:27 according to the RFC1123 spec" in {
      Rfc1123.format {
        make(new GregorianCalendar(TimeZone.getTimeZone("GMT"))) {
          _.set(2011, 3, 28, 12, 56, 27)
        }.getTime
      } mustEqual "Thu, 28 Apr 2011 12:56:27 GMT"
    }
  }
  
}