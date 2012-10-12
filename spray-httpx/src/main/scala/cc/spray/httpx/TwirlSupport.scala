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

package spray.httpx

import marshalling.Marshaller
import twirl.api.{Xml, Txt, Html}
import spray.http._
import MediaTypes._


/**
 * A trait providing Marshallers for the Twirl template result types.
 */
trait TwirlSupport {

  implicit val twirlHtmlMarshaller =
    twirlMarshaller[Html](`text/html`, `application/xhtml+xml`)

  implicit val twirlTxtMarshaller =
    twirlMarshaller[Txt](ContentType.`text/plain`)

  implicit val twirlXmlMarshaller =
    twirlMarshaller[Xml](`text/xml`, `text/html`, `application/xhtml+xml`)

  protected def twirlMarshaller[T](marshalTo: ContentType*): Marshaller[T] =
    Marshaller.delegate[T, String](marshalTo: _*)(_.toString)
}

object TwirlSupport extends TwirlSupport