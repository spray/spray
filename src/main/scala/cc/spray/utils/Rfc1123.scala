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

import java.text.SimpleDateFormat
import java.util.{Date, TimeZone, Locale}

object Rfc1123 {

  val Pattern = "EEE, dd MMM yyyy HH:mm:ss zzz"
  
  val Format = make(new SimpleDateFormat(Pattern, Locale.US)) {
    _.setTimeZone(TimeZone.getTimeZone("GMT"))
  }
  
  def now: String = format(new Date)
  
  def format(date: Date): String = Format.format(date)
  
}