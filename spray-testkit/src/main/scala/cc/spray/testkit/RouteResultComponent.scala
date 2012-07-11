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

package cc.spray.testkit

import collection.mutable.ListBuffer
import akka.util.Duration
import java.util.concurrent.{TimeUnit, CountDownLatch}
import akka.actor.{ActorRef, ActorSystem}
import akka.spray.UnregisteredActorRef
import cc.spray.routing.{Rejected, Rejection}
import cc.spray.http._
import cc.spray.util.NotImplementedException


trait RouteResultComponent {
  def system: ActorSystem
  def failTest(msg: String): Nothing

  /**
   * A receptacle for the response, rejections and potentially generated response chunks created by a route.
   */
  class RouteResult(timeout: Duration) { outer =>
    private[this] var _response: Option[HttpResponse] = None
    private[this] var _rejections: Option[Seq[Rejection]] = None
    private[this] val _chunks = ListBuffer.empty[MessageChunk]
    private[this] var _closingExtensions: List[ChunkExtension] = Nil
    private[this] var _trailer: List[HttpHeader] = Nil
    private[this] val latch = new CountDownLatch(1)
    private[this] var virginal = true

    private[testkit] val handler = new UnregisteredActorRef(system) {
      def handle(message: Any, sender: ActorRef) {
        message match {
          case x: HttpResponse =>
            saveResult(Right(x))
            latch.countDown()
          case Rejected(rejections) =>
            saveResult(Left(rejections))
            latch.countDown()
          case ChunkedResponseStart(x) =>
            throw new NotImplementedException // TODO
          case MessageChunk(buffer, extensions) =>
            throw new NotImplementedException // TODO
          case ChunkedMessageEnd(extensions, trailer) =>
            throw new NotImplementedException // TODO
        }
      }
    }

    private[testkit] def awaitResult: this.type = {
      latch.await(timeout.toMillis, TimeUnit.MILLISECONDS)
      this
    }

    private def saveResult(result: Either[Seq[Rejection], HttpResponse]) {
      synchronized {
        if (!virginal) failTest("Route completed/rejected more than once")
        result match {
          case Right(resp) => _response = Some(resp)
          case Left(rejs) => _rejections = Some(rejs)
        }
        virginal = false
      }
    }

    private def failNotCompletedNotRejected(): Nothing =
      failTest("Request was neither completed nor rejected within " + timeout)

    def handled: Boolean = synchronized { _response.isDefined }
    def response: HttpResponse = synchronized {
      _response.getOrElse {
        _rejections.foreach(r => failTest("Request was rejected with " + r))
        failNotCompletedNotRejected()
      }
    }
    def rejections: Seq[Rejection] = synchronized {
      _rejections.getOrElse {
        _response.foreach(resp => failTest("Request was not rejected, response was " + resp))
        failNotCompletedNotRejected()
      }
    }
    def chunks: List[MessageChunk] = synchronized { _chunks.toList }
    def closingExtensions = synchronized { _closingExtensions }
    def trailer = synchronized { _trailer }

    def ~> [T](f: RouteResult => T): T = f(this)
  }

  case class RouteTestTimeout(duration: Duration)
}