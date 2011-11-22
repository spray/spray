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

import http._
import utils._

abstract class SimpleMarshaller[A] extends Marshaller[A] {

  def apply(accept: ContentType => Option[ContentType]) = {
    canMarshalTo.mapFind(accept) match {
      case Some(contentType) => MarshalWith[A](ctx => value => ctx.marshalTo(marshal(value, contentType)))
      case None => CantMarshal(canMarshalTo)
    }
  }

  def canMarshalTo: List[ContentType]

  def marshal(value: A, contentType: ContentType): HttpContent
  
} 