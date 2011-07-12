/*
 * Copyright (C) 2011 Mathias Doenitz
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team (http://github.com/jdegoes/blueeyes)
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

package cc.spray.http

/**
 * Represents a point in time as the number of milli seconds
 * since the start of "the epoch", namely January 1, 1970, 00:00:00 GMT.
 */
case class HttpDateTime(clicks: Long) {

  // based on a fast RFC1123 implementation (C) 2000 by Tim Kientzle <kientzle@acm.org>
  lazy val (year, month, day, hour, minute, second, weekday) = {
    // compute day number, seconds since beginning of day
    var s = clicks
    if (s >= 0) s /= 1000 // seconds since 1 Jan 1970
    else s = (s - 999 ) / 1000 // floor(sec/1000)

    var dn = (s / 86400).toInt
    s %= 86400 // positive seconds since beginning of day
    if (s < 0) { s += 86400; dn -= 1 }
    dn += 1969 * 365 + 492 - 19 + 4 // days since "1 Jan, year 1"

    // convert days since 1 Jan, year 1 to year/yearday
    var y = 400 * (dn / 146097).toInt + 1
    var d = dn % 146097
    if (d == 146096) { y += 399; d = 365 } // last year of 400 is long
    else {
      y += 100 * (d / 36524)
      d %= 36524
      y += 4 * (d / 1461)
      d %= 1461
      if (d == 1460) { y += 3; d=365 } // last year out of 4 is long
      else {
        y += d / 365
        d %= 365
      }
    }

    val isleap = ((y % 4 == 0) && !(y % 100 == 0)) || (y % 400 == 0)

    // compute month/monthday from year/yearday
    if (!isleap && (d >= 59)) d +=1 // skip non-existent Feb 29
    if (d >= 60) d += 1 // skip non-existent Feb 30
    var mon = ((d % 214) / 61) * 2 + ((d % 214) % 61) / 31
    if (d > 213) mon += 7
    d = ((d % 214) % 61) % 31 + 1

    // convert second to hour/min/sec
    var m = (s / 60).toInt
    val h = m / 60
    m %= 60
    s %= 60
    val w = (dn + 1) % 7 // day of week, 0==Sun

    (y, mon, d, h, m, s, w)
  }

  def toIsoDateTimeString = {
    year + "-" +
    ((month + 1) / 10 + '0').toChar + ((month + 1) % 10 + '0').toChar + '-' +
    (day / 10 + '0').toChar + (day % 10 + '0').toChar + 'T' +
    (hour / 10 + '0').toChar + (hour % 10 + '0').toChar + ':' +
    (minute / 10 + '0').toChar + (minute % 10 + '0').toChar + ':' +
    (second / 10 + '0').toChar + (second % 10 + '0').toChar
  }

  /* RFC 1123 date string: "Sun, 06 Nov 1994 08:49:37 GMT" */
  def toRfc1123DateTimeString = {
    new java.lang.StringBuilder(32)
      .append(HttpDateTime.WEEKDAYS(weekday)).append(", ")
      .append((day / 10 + '0').toChar).append((day % 10 + '0').toChar).append(' ')
      .append(HttpDateTime.MONTHS(month)).append(' ')
      .append(year).append(' ')
      .append((hour / 10 + '0').toChar).append((hour % 10 + '0').toChar).append(':')
      .append((minute / 10 + '0').toChar).append((minute % 10 + '0').toChar).append(':')
      .append((second / 10 + '0').toChar).append((second % 10 + '0').toChar).append(" GMT")
      .toString
  }
}

object HttpDateTime {
  val WEEKDAYS = Array("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
  val MONTHS = Array("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
}