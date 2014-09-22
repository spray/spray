package spray.httpx

import play.twirl.api.{ Xml, Txt, Html }
import spray.http._
import MediaTypes._
import TwirlSupport.twirlMarshaller

/**
 * A trait providing Marshallers for the Play Twirl template result types.
 *
 * Import this support for use with the new Play Twirl plugin ("com.typesafe.sbt" % "sbt-twirl" % "1.0.0").
 * For the old spray Twirl plugin use ``TwirlSupport`` instead.
 */
trait PlayTwirlSupport {
  implicit val twirlHtmlMarshaller =
    twirlMarshaller[Html](`text/html`, `application/xhtml+xml`)

  implicit val twirlTxtMarshaller =
    twirlMarshaller[Txt](ContentTypes.`text/plain`)

  implicit val twirlXmlMarshaller =
    twirlMarshaller[Xml](`text/xml`)
}

object PlayTwirlSupport extends PlayTwirlSupport