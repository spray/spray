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
package spray.can.server

import scala.annotation.tailrec
import scala.concurrent.duration._
import spray.http.{ Timedout, HttpMessageStart }
import spray.can.rendering.ResponsePartRenderingContext
import spray.can.server.RequestParsing.HttpMessageStartEvent
import spray.io._
import spray.can.Http
import spray.util.{ Timestamp, PaddedAtomicLong }

object StatsSupport {

  class StatsHolder {
    val startTimestamp = Timestamp.now
    val requestStarts = new PaddedAtomicLong
    val responseStarts = new PaddedAtomicLong
    val maxOpenRequests = new PaddedAtomicLong
    val connectionsOpened = new PaddedAtomicLong
    val connectionsClosed = new PaddedAtomicLong
    val maxOpenConnections = new PaddedAtomicLong
    val requestTimeouts = new PaddedAtomicLong

    @tailrec
    final def adjustMaxOpenConnections(): Unit = {
      val co = connectionsOpened.get
      val cc = connectionsClosed.get
      val moc = maxOpenConnections.get
      val currentMoc = co - cc
      if (currentMoc > moc)
        if (!maxOpenConnections.compareAndSet(moc, currentMoc)) adjustMaxOpenConnections()
    }

    @tailrec
    final def adjustMaxOpenRequests(): Unit = {
      val rqs = requestStarts.get
      val rss = responseStarts.get
      val mor = maxOpenRequests.get
      val currentMor = rqs - rss
      if (currentMor > mor)
        if (!maxOpenRequests.compareAndSet(mor, currentMor)) adjustMaxOpenRequests()
    }

    def toStats = Stats(
      uptime = (Timestamp.now - startTimestamp).asInstanceOf[FiniteDuration],
      totalRequests = requestStarts.get,
      openRequests = requestStarts.get - responseStarts.get,
      maxOpenRequests = maxOpenRequests.get,
      totalConnections = connectionsOpened.get,
      openConnections = connectionsOpened.get - connectionsClosed.get,
      maxOpenConnections = maxOpenConnections.get,
      requestTimeouts = requestTimeouts.get)

    def clear(): Unit = {
      requestStarts.set(0L)
      responseStarts.set(0L)
      maxOpenRequests.set(0L)
      connectionsOpened.set(0L)
      connectionsClosed.set(0L)
      maxOpenConnections.set(0L)
      requestTimeouts.set(0L)
    }
  }

  def apply(holder: StatsHolder): PipelineStage =
    new PipelineStage {
      def apply(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines =
        new Pipelines {
          import holder._
          connectionsOpened.incrementAndGet()
          adjustMaxOpenConnections()

          val commandPipeline: CPL = {
            case x: ResponsePartRenderingContext if x.responsePart.isInstanceOf[HttpMessageStart] ⇒
              responseStarts.incrementAndGet()
              commandPL(x)

            case x @ Pipeline.Tell(_, _: Timedout, _) ⇒
              requestTimeouts.incrementAndGet()
              commandPL(x)

            case cmd ⇒ commandPL(cmd)
          }

          val eventPipeline: EPL = {
            case ev: HttpMessageStartEvent ⇒
              requestStarts.incrementAndGet()
              adjustMaxOpenRequests()
              eventPL(ev)

            case x: Http.ConnectionClosed ⇒
              connectionsClosed.incrementAndGet()
              eventPL(x)

            case ev ⇒ eventPL(ev)
          }
        }
    }
}

//# Stats
case class Stats(
  uptime: FiniteDuration,
  totalRequests: Long,
  openRequests: Long,
  maxOpenRequests: Long,
  totalConnections: Long,
  openConnections: Long,
  maxOpenConnections: Long,
  requestTimeouts: Long)
//#
