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
package marshalling

import http._
import MediaTypes._
import xml.NodeSeq

trait DefaultMarshallers {

  implicit object StringMarshaller extends MarshallerBase[String] {
    val canMarshalTo = List(ContentType(`text/plain`)) 

    def marshal(value: String, contentType: ContentType) = HttpContent(contentType, value)
  }
  
  implicit object NodeSeqMarshaller extends MarshallerBase[NodeSeq] {
    val canMarshalTo = List(ContentType(`text/xml`), ContentType(`text/html`), ContentType(`application/xhtml+xml`)) 

    def marshal(value: NodeSeq, contentType: ContentType) = StringMarshaller.marshal(value.toString, contentType)
  }
  
}

object DefaultMarshallers extends DefaultMarshallers