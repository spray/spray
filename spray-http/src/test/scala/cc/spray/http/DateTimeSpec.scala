package cc.spray
package http

import org.specs.Specification
import java.util.TimeZone
import util.Random
import org.specs.matcher.Matcher

class DateTimeSpec extends Specification {

  val GMT = TimeZone.getTimeZone("GMT")
  val specificClicks = DateTime(2011, 7, 12, 14, 8, 12).clicks
  val startClicks = DateTime(1800, 1, 1, 0, 0, 0).clicks
  val maxClickDelta = DateTime(2199, 12, 31, 23, 59, 59).clicks - startClicks
  val random = new Random()
  val httpDateTimes = Stream.continually {
    DateTime(startClicks + math.abs(random.nextLong()) % maxClickDelta)
  }

  "DateTime.toRfc1123DateTimeString" should {
    "properly print a known date" in {
      DateTime(specificClicks).toRfc1123DateTimeString mustEqual "Tue, 12 Jul 2011 14:08:12 GMT"
      DateTime(2011, 7, 12, 14, 8, 12).toRfc1123DateTimeString mustEqual "Tue, 12 Jul 2011 14:08:12 GMT"
    }
    "behave exactly as a corresponding formatting via SimpleDateFormat" in {
      val Rfc1123Format = {
        val fmt = new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", java.util.Locale.US)
        fmt.setTimeZone(GMT)
        fmt
      }
      val matchSimpleDateFormat = new Matcher[Iterable[DateTime]] {
        def apply(dateTimes: => Iterable[DateTime]) = {
          def rfc1123Format(dt: DateTime) = Rfc1123Format.format(new java.util.Date(dt.clicks))
          dateTimes.find(dt => dt.toRfc1123DateTimeString != rfc1123Format(dt)) match {
            case Some(dt) => (false, "", dt.toRfc1123DateTimeString + " != " + rfc1123Format(dt))
            case None => (true, "no errors", "")
          }
        }
      }
      httpDateTimes.take(10000) must matchSimpleDateFormat
    }
  }

  "DateTime.toIsoDateTimeString" should {
    "properly print a known date" in {
      DateTime(specificClicks).toIsoDateTimeString mustEqual "2011-07-12T14:08:12"
    }
  }

  "The two DateTime implementations" should {
    "allow for transparent round-trip conversions" in {
      val roundTripOk = new Matcher[Iterable[DateTime]] {
        def apply(dateTimes: => Iterable[DateTime]) = {
          def roundTrip(dt: DateTime) = DateTime(dt.year, dt.month, dt.day, dt.hour, dt.minute, dt.second)
          def matches(a: DateTime, b: DateTime) = a == b && a.weekday == b.weekday
          dateTimes.find(dt => !matches(dt, roundTrip(dt))) match {
            case Some(dt) => (false, "", dt.toRfc1123DateTimeString + " != " + roundTrip(dt).toRfc1123DateTimeString)
            case None => (true, "no errors", "")
          }
        }
      }
      httpDateTimes.take(10000) must roundTripOk
    }
  }
}