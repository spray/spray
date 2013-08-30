/*
 * Copyright (C) 2011-2013 spray.io
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

package spray.http

trait MessagePredicate extends (HttpMessage ⇒ Boolean) { self ⇒
  def &&(that: MessagePredicate): MessagePredicate = new MessagePredicate {
    def apply(msg: HttpMessage) = self(msg) && that(msg)
  }
  def ||(that: MessagePredicate) = new MessagePredicate {
    def apply(msg: HttpMessage) = self(msg) || that(msg)
  }
  def unary_! = new MessagePredicate {
    def apply(msg: HttpMessage) = !self(msg)
  }
}

object MessagePredicate {
  implicit def apply(f: HttpMessage ⇒ Boolean): MessagePredicate =
    new MessagePredicate {
      def apply(msg: HttpMessage) = f(msg)
    }

  def isRequest = apply(_.isRequest)
  def isResponse = apply(_.isResponse)
  def minEntitySize(minSize: Int) = apply(_.entity.data.length >= minSize)
  def responseStatus(f: StatusCode ⇒ Boolean) = apply {
    case x: HttpResponse ⇒ f(x.status)
    case _: HttpRequest  ⇒ false
  }
  def isCompressible: MessagePredicate = apply {
    _.entity match {
      case HttpEntity.NonEmpty(contentType, _) ⇒ contentType.mediaType.compressible
      case _                                   ⇒ false
    }
  }
}