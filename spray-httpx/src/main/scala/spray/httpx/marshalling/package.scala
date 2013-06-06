/*
 * Copyright (C) 2011-2013 spray.io
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
import spray.http.HttpEntity

package object marshalling {

  def marshal[T](value: T)(implicit marshaller: Marshaller[T], actorRefFactory: ActorRefFactory = null,
                           timeout: Timeout = 1.second): Either[Throwable, HttpEntity] = {
    val ctx = marshalCollecting(value)
    ctx.entity match {
      case Some(entity) ⇒ Right(entity)
      case None ⇒
        Left(ctx.error.getOrElse(new RuntimeException("Marshaller for %s did not produce result" format value)))
    }
  }

  def marshalCollecting[T](value: T)(implicit marshaller: Marshaller[T], actorRefFactory: ActorRefFactory = null,
                                     timeout: Timeout = 1.second): CollectingMarshallingContext = {
    val ctx = new CollectingMarshallingContext
    try {
      marshaller(value, ctx)
      ctx.awaitResults
    } catch {
      case NonFatal(e) ⇒ ctx.handleError(e)
    }
    ctx
  }

  def marshalUnsafe[T: Marshaller](value: T): HttpEntity = marshal(value).fold(throw _, identityFunc)
}

