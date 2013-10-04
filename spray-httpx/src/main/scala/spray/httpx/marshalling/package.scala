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

import akka.util.duration._
import akka.util.NonFatal
import akka.util.Timeout
import akka.actor.ActorRefFactory
import spray.util.identityFunc
import spray.http.{ HttpHeader, HttpEntity }

package object marshalling {

  def marshal[T](value: T, ctx: CollectingMarshallingContext = new CollectingMarshallingContext)(implicit marshaller: Marshaller[T], actorRefFactory: ActorRefFactory = null,
                                                                                                 timeout: Timeout = 1.second): Either[Throwable, HttpEntity] =
    marshalToEntityAndHeaders(value, ctx).right.map(_._1)

  def marshalToEntityAndHeaders[T](value: T, ctx: CollectingMarshallingContext = new CollectingMarshallingContext)(implicit marshaller: Marshaller[T], actorRefFactory: ActorRefFactory = null,
                                                                                                                   timeout: Timeout = 1.second): Either[Throwable, (HttpEntity, Seq[HttpHeader])] = {
    marshalCollecting(value, ctx)
    ctx.entityAndHeaders match {
      case Some(value) ⇒ Right(value)
      case None ⇒
        Left(ctx.error.getOrElse(new RuntimeException("Marshaller for %s did not produce result" format value)))
    }
  }

  def marshalCollecting[T](value: T, ctx: CollectingMarshallingContext)(implicit marshaller: Marshaller[T], actorRefFactory: ActorRefFactory = null,
                                                                        timeout: Timeout = 1.second): Unit =
    try {
      marshaller(value, ctx)
      ctx.awaitResults
    } catch {
      case NonFatal(e) ⇒ ctx.handleError(e)
    }

  def marshalUnsafe[T: Marshaller](value: T): HttpEntity = marshal(value).fold(throw _, identityFunc)
}

