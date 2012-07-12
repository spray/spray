/*
 * Copyright (C) 2011-2012 spray.cc
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

package cc.spray.httpx

import cc.spray.http.{ContentType, HttpEntity}
import akka.util.NonFatal


package object marshalling {

  type ContentTypeSelector = ContentType => Option[ContentType]
  type AcceptableContentTypes = Seq[ContentType]

  def marshal[T](value: T)(implicit marshaller: Marshaller[T]): Either[Throwable, HttpEntity] = {
    try {
      var result: Option[Either[Throwable, HttpEntity]] = None
      marshaller(Some(_)) match { // we always convert to the first CT the marshaller can marshal to
        case Right(marshalling) =>
          val ctx = new MarshallingContext {
            def marshalTo(entity: HttpEntity) { result = Some(Right(entity)) }
            def handleError(error: Throwable) { result = Some(Left(error)) }
            def startChunkedMessage(entity: HttpEntity) = throw new UnsupportedOperationException
          }
          marshalling.runSafe(value, ctx)
        case Left(_) =>
          // our selector never rejects a content-type, so why does the marshaller not produce a marshalling?
          throw new IllegalStateException
      }
      result.getOrElse(sys.error("Marshaller for %s did not produce result" format value))
    } catch {
      case NonFatal(e) => Left(e)
    }
  }
}