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
package test

import collection.mutable.ListBuffer
import http.{HttpResponse, HttpHeader, ChunkExtension, MessageChunk}
import akka.util.Duration
import java.util.concurrent.{TimeUnit, CountDownLatch}
import typeconversion.ChunkSender
import akka.dispatch.AlreadyCompletedFuture

trait RouteResultComponent {

  def doFail(msg: String): Nothing

  /**
   * A receptacle for the response, rejections and potentially generated response chunks created by a route.
   */
  class RouteResult { outer =>
    var response: Option[HttpResponse] = None
    var rejections: Option[Set[Rejection]] = None
    val chunks = ListBuffer.empty[MessageChunk]
    var closingExtensions: List[ChunkExtension] = Nil
    var trailer: List[HttpHeader] = Nil
    private val latch = new CountDownLatch(1)
    private var virginal = true

    def requestResponder = RequestResponder(
      complete = resp => { saveResult(Right(resp)); latch.countDown() },
      reject = rejs => { saveResult(Left(rejs)); latch.countDown() },
      startChunkedResponse = resp => { saveResult(Right(resp)); new TestChunkSender() },
      resetConnectionTimeout = () => ()
    )

    def awaitResult(timeout: Duration) {
      latch.await(timeout.toMillis, TimeUnit.MILLISECONDS)
    }

    private def saveResult(result: Either[Set[Rejection], HttpResponse]) {
      synchronized {
        if (!virginal) doFail("Route completed/rejected more than once")
        result match {
          case Right(resp) => response = Some(resp)
          case Left(rejs) => rejections = Some(rejs)
        }
        virginal = false
      }
    }

    class TestChunkSender(onSent: Option[Long => Unit] = None) extends ChunkSender {
      def sendChunk(chunk: MessageChunk) = outer.synchronized {
        chunks += chunk
        new AlreadyCompletedFuture(Right(()))
      }

      def close(extensions: List[ChunkExtension], trailer: List[HttpHeader]) {
        outer.synchronized {
          if (latch.getCount == 0) doFail("`close` called more than once")
          closingExtensions = extensions
          outer.trailer = trailer
          latch.countDown()
        }
      }
    }
  }

}