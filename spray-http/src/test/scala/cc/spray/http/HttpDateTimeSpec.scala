package cc.spray
package http

import org.specs.Specification
import java.util.{TimeZone, GregorianCalendar}
import util.Random
import org.specs.matcher.Matcher

class HttpDateTimeSpec extends Specification {

  val GMT = TimeZone.getTimeZone("GMT")
  val Rfc1123Format = {
    TimeZone.setDefault(GMT)
    new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", java.util.Locale.UK)
  }
  val cal = new GregorianCalendar(GMT)
  val specificClicks = { cal.set(2011, 6, 12, 14, 8, 12); cal.getTimeInMillis }
  val startClicks = { cal.set(1850, 0, 1, 0, 0, 0); cal.getTimeInMillis }
  val maxClickDelta = { cal.set(2150, 11, 31, 23, 59, 59); cal.getTimeInMillis - startClicks }

  "HttpDateTime.toRfc1123DateTimeString" should {
    "properly print a known date" in {
      HttpDateTime(specificClicks).toRfc1123DateTimeString mustEqual "Tue, 12 Jul 2011 14:08:12 GMT"
    }
    "behave exactly as a corresponding formatting via SimpleDateFormat" in {
      val random = new Random()
      val httpDateTimes = Stream.continually {
        HttpDateTime(startClicks + math.abs(random.nextLong()) % maxClickDelta)
      }
      val matchSimpleDateFormat = new Matcher[Iterable[HttpDateTime]] {
        def apply(dateTimes: => Iterable[HttpDateTime]) = {
          def rfc1123Format(dt: HttpDateTime) = Rfc1123Format.format(new java.util.Date(dt.clicks))
          dateTimes.find(dt => dt.toRfc1123DateTimeString != rfc1123Format(dt)) match {
            case Some(dt) => (false, "", dt.toRfc1123DateTimeString + " != " + rfc1123Format(dt))
            case None => (true, "no errors", "")
          }
        }
      }
      httpDateTimes.take(10000) must matchSimpleDateFormat
    }
  }

  "HttpDateTime.toRfc1123DateTimeString" should {
    "properly print a known date" in {
      HttpDateTime(specificClicks).toIsoDateTimeString mustEqual "2011-07-12T14:08:12"
    }
  }
}