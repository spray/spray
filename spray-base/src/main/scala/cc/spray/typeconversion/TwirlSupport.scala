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
package typeconversion

import http.ContentType
import http.MediaTypes._
import twirl.api.{Xml, Txt, Html}

/**
 * A trait providing Marshallers for the Twirl template result types.
 * Note: This trait will be moved into spray with 0.9.0-RC2
 */
trait TwirlSupport {

  implicit lazy val twirlHtmlMarshaller = twirlMarshaller[Html] {
    ContentType(`text/html`) :: ContentType(`application/xhtml+xml`) :: Nil
  }

  implicit lazy val twirlTxtMarshaller = twirlMarshaller[Txt] {
    ContentType(`text/plain`) :: Nil
  }

  implicit lazy val twirlXmlMarshaller = twirlMarshaller[Xml] {
    ContentType(`text/xml`) :: ContentType(`text/html`) :: ContentType(`application/xhtml+xml`) :: Nil
  }

  protected def twirlMarshaller[T](contentTypes: List[ContentType]) = new SimpleMarshaller[T] {
    def canMarshalTo = contentTypes
    def marshal(value: T, contentType: ContentType) =
      DefaultMarshallers.StringMarshaller.marshal(value.toString, contentType)
  }

}

object TwirlSupport extends TwirlSupport
