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

import akka.actor.ActorRef
import akka.util.NonFatal
import cc.spray.http.{HttpEntity, ContentType}
import cc.spray.util._


trait Marshaller[-T] extends (ContentTypeSelector => Either[AcceptableContentTypes, Marshalling[T]])

trait Marshalling[-T] extends ((T, MarshallingContext) => Unit) {
  def runSafe(value: T, ctx: MarshallingContext) {
    try apply(value, ctx)
    catch { case NonFatal(e) => ctx.handleError(e) }
  }
}

object Marshaller extends BasicMarshallers
  with MetaMarshallers
  with StreamMarshallers
  with MultipartMarshallers {

  def apply[T](marshalTo: ContentType*)(f: (T, ContentType, MarshallingContext) => Unit): Marshaller[T] =
    new Marshaller[T] {
      def apply(selector: ContentTypeSelector) = marshalTo.mapFind(selector) match {
        case Some(contentType) => Right {
          new Marshalling[T] {
            def apply(value: T, ctx: MarshallingContext) {
              f(value, contentType, ctx)
            }
          }
        }
        case None => Left(marshalTo)
      }
    }

  def delegate[A, B](marshalTo: ContentType*)(f: (A, ContentType) => B)(implicit mb: Marshaller[B]): Marshaller[A] =
    new Marshaller[A] {
      def apply(selector: ContentTypeSelector) = marshalTo.mapFind(selector) match {
        case Some(contentType) => mb(Some(_)).right.map { marshallingB =>
          new Marshalling[A] {
            def apply(value: A, ctx: MarshallingContext) {
              marshallingB(f(value, contentType), ctx.withContentTypeOverriding(contentType))
            }
          }
        }
        case None => Left(marshalTo)
      }
    }
}

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
  def startChunkedMessage(entity: HttpEntity): ActorRef

  /**
   * Creates a new MarshallingContext based on this one, that overrides the Content-Type of the produced entity
   * with the given one.
   */
  def withContentTypeOverriding(contentType: ContentType): MarshallingContext =
   new MarshallingContext {
     def marshalTo(entity: HttpEntity) { self.marshalTo(overrideContentType(entity)) }
     def handleError(error: Throwable) { self.handleError(error) }
     def startChunkedMessage(entity: HttpEntity) = self.startChunkedMessage(overrideContentType(entity))
     def overrideContentType(entity: HttpEntity) = entity.map((ct, buf) => (contentType, buf))
   }
}
