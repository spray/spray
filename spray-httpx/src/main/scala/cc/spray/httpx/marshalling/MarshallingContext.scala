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

package cc.spray.httpx.marshalling

import cc.spray.http.{ContentType, HttpEntity}
import akka.actor.ActorRef


trait MarshallingContext { self =>
  /**
   * Receives the end product entity of a Marshalling.
   */
  def marshalTo(entity: HttpEntity)

  /**
   * Handles the given error.
   * Calling this method rather than throwing the exception directly allows the error to transcend thread boundaries
   * and contexts, e.g. when channeling an error from a future execution back to the original scope.
   */
  def handleError(error: Throwable)

  /**
   * Uses the given entity to start a chunked response stream.
   * The method returns an ActorRef that should be used as the channel for subsequent [[cc.spray.http.MessageChunk]]
   * instances and the finalizing [[cc.spray.http.ChunkedMessageEnd]].
   */
  def startChunkedMessage(entity: HttpEntity)(implicit sender: ActorRef): ActorRef

  /**
   * Creates a new MarshallingContext based on this one, that overrides the Content-Type of the produced entity
   * with the given one.
   */
  def withContentTypeOverriding(contentType: ContentType): MarshallingContext =
   new MarshallingContext {
     def marshalTo(entity: HttpEntity) { self.marshalTo(overrideContentType(entity)) }
     def handleError(error: Throwable) { self.handleError(error) }
     def startChunkedMessage(entity: HttpEntity)(implicit sender: ActorRef) =
       self.startChunkedMessage(overrideContentType(entity))
     def overrideContentType(entity: HttpEntity) = entity.map((ct, buf) => (contentType, buf))
   }
}
