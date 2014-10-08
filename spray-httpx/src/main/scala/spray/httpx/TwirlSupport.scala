/*
 * Copyright © 2011-2013 the spray project <http://spray.io>
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

package spray.httpx

import marshalling.Marshaller
import twirl.api.{ Xml, Txt, Html }
import spray.http._
import MediaTypes._

/**
 * A trait providing Marshallers for the Twirl template result types.
 *
 * Import this support for use with the old spray Twirl plugin ("io.spray" % "sbt-twirl" % "0.7.0").
 * For the new Play Twirl plugin use ``PlayTwirlSupport`` instead.
 */
trait TwirlSupport {

  implicit val twirlHtmlMarshaller =
    twirlMarshaller[Html](`text/html`, `application/xhtml+xml`)

  implicit val twirlTxtMarshaller =
    twirlMarshaller[Txt](ContentTypes.`text/plain`)

  implicit val twirlXmlMarshaller =
    twirlMarshaller[Xml](`text/xml`)

  protected[httpx] def twirlMarshaller[T](marshalTo: ContentType*): Marshaller[T] =
    Marshaller.delegate[T, String](marshalTo: _*)(_.toString)
}

object TwirlSupport extends TwirlSupport