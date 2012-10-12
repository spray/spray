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

package spray.http

/**
 * Immutable, fast and efficient Date + Time implementation without any dependencies.
 * Does not support TimeZones, all DateTime values are always GMT based.
 */
sealed abstract class DateTime extends Ordered[DateTime] {
  /**
   * The year.
   */
  def year: Int

  /**
   * The month of the year. January is 1.
   */
  def month: Int

  /**
   * The day of the month. The first day is 1.
   */
  def day: Int

  /**
   * The hour of the day. The first hour is 0.
   */
  def hour: Int

  /**
   * The minute of the hour. The first minute is 0.
   */
  def minute: Int

  /**
   * The second of the minute. The first second is 0.
   */
  def second: Int

  /**
   * The day of the week. Sunday is 0.
   */
  def weekday: Int

  /**
   * The day of the week as a 3 letter abbreviation:
   * `Sun`, `Mon`, `Tue`, `Wed`, `Thu`, `Fri` or `Sat`
   */
  def weekdayStr: String = DateTime.WEEKDAYS(weekday)

  /**
   * The day of the month as a 3 letter abbreviation:
   * `Jan`, `Feb`, `Mar`, `Apr`, `May`, `Jun`, `Jul`, `Aug`, `Sep`, `Oct`, `Nov` or `Dec`
   */
  def monthStr: String = DateTime.MONTHS(month - 1)

  /**
   * True if leap year.
   */
  def isLeapYear: Boolean

  /**
   * The number of milliseconds since the start of "the epoch", namely January 1, 1970, 00:00:00 GMT.
   */
  def clicks: Long

  /**
   * Creates a new `DateTime` that represents the point in time the given number of ms later.
   */
  def + (millis: Long): DateTime = DateTime(clicks + millis)

  /**
   * Creates a new `DateTime` that represents the point in time the given number of ms earlier.
   */
  def - (millis: Long): DateTime = DateTime(clicks - millis)

  /**
   * `yyyy-mm-dd`
   */
  def toIsoDateString = year + "-" + ##(month) + '-' + ##(day)

  /**
   * `yyyy-mm-ddThh:mm:ss`
   */
  def toIsoDateTimeString = toIsoDateString + 'T' + ##(hour) + ':' + ##(minute) + ':' + ##(second)

  /**
   * RFC 1123 date string, e.g. `Sun, 06 Nov 1994 08:49:37 GMT`
   */
  def toRfc1123DateTimeString = {
    new java.lang.StringBuilder(32)
      .append(weekdayStr).append(", ")
      .append(##(day)).append(' ')
      .append(monthStr).append(' ')
      .append(year).append(' ')
      .append(##(hour)).append(':')
      .append(##(minute)).append(':')
      .append(##(second)).append(" GMT")
      .toString
  }

  private def ##(i: Int) = String.valueOf(Array[Char]((i / 10 + '0').toChar, (i % 10 + '0').toChar))

  def compare(that: DateTime) = {
    if (clicks < that.clicks) -1 else if (clicks > that.clicks) 1 else 0
  }

  override def hashCode() = clicks.##

  override def equals(obj: Any) = obj match {
    case x: DateTime => x.clicks == clicks
    case _ => false
  }

  override def toString = toIsoDateTimeString
}

object DateTime {
  val WEEKDAYS = Array("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
  val MONTHS = Array("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
  val MinValue = DateTime(1800, 1, 1)
  val MaxValue = DateTime(2199, 12, 31, 23, 59, 59)

  /**
   * Creates a new `DateTime` with the given properties.
   */
  def apply(year_ :Int, month_ :Int, day_ :Int, hour_ :Int = 0, minute_ :Int = 0, second_ :Int = 0): DateTime = new DateTime {
    val year = check(year_, 1800 <= year_ && year_ <= 2199, "year must be >= 1800 and <= 2199")
    val month = check(month_, 1 <= month_ && month_ <= 12, "month must be >= 1 and <= 12")
    val day = check(day_, 1 <= day_ && day_ <= 31, "day must be >= 1 and <= 31")
    val hour = check(hour_, 0 <= hour_ && hour_ <= 23, "hour must be >= 0 and <= 23")
    val minute = check(minute_, 0 <= minute_ && minute_ <= 59, "minute_ must be >= 0 and <= 59")
    val second = check(second_, 0 <= second_ && second_ <= 59, "second must be >= 0 and <= 59")

    val (clicks, weekday) = {
      // compute yearday from month/monthday
      val m = month - 1
      var d = (m % 7) * 30 + (m % 7 + 1) / 2 + day
      if (m >= 7) d += 214
      if (d >= 61) d -= 1 // skip non-existent Feb 30
      if (!isLeapYear && (d >= 60)) d -=1 // skip non-existent Feb 29

      // convert year/yearday to days since Jan 1, 1970, 00:00:00
      val y = year - 1
      d += y * 365 + y / 4 - y / 100 + y / 400
      val dn = d - (1969 * 365 + 492 - 19 + 4)

      val c = (dn - 1) * 86400L + hour * 3600L + minute * 60L + second // seconds since Jan 1, 1970, 00:00:00
      (c * 1000, d % 7)
    }

    def isLeapYear = ((year % 4 == 0) && !(year % 100 == 0)) || (year % 400 == 0)

    private def check(x: Int, test: Boolean, msg: String) = { require(test, msg); x }
  }

  /**
   * Creates a new `DateTime` from the number of milli seconds
   * since the start of "the epoch", namely January 1, 1970, 00:00:00 GMT.
   */
  def apply(clicks_ :Long): DateTime = new DateTime {
    val clicks = clicks_ - clicks_ % 1000

    require(DateTime.MinValue <= this && this <= DateTime.MaxValue,
      "DateTime value must be >= " + DateTime.MinValue + " and <= " + DateTime.MaxValue)

    // based on a fast RFC1123 implementation (C) 2000 by Tim Kientzle <kientzle@acm.org>
    val (year, month, day, hour, minute, second, weekday, isLeapYear) = {
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

      val isLeap = ((y % 4 == 0) && !(y % 100 == 0)) || (y % 400 == 0)

      // compute month/monthday from year/yearday
      if (!isLeap && (d >= 59)) d +=1 // skip non-existent Feb 29
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

      (y, mon + 1, d, h, m, s.toInt, w, isLeap)
    }
  }

  /**
   * Creates a new `DateTime` instance for the current point in time.
   */
  def now: DateTime = apply(System.currentTimeMillis)
}