/*
 * Copyright (C) 2011-2012 spray.io
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

import java.util.concurrent.atomic.AtomicLong
import annotation.tailrec
import akka.util.Duration
import java.util.concurrent.TimeUnit
import spray.http.{Timeout, HttpMessageStart}
import spray.can.rendering.HttpResponsePartRenderingContext
import spray.can.server.RequestParsing.HttpMessageStartEvent
import spray.util.IdleTimeout
import spray.io._


object StatsSupport {

  class StatsHolder {
    val startTimestamp     = System.currentTimeMillis
    val requestStarts      = new AtomicLong
    val responseStarts     = new AtomicLong
    val maxOpenRequests    = new AtomicLong
    val connectionsOpened  = new AtomicLong
    val connectionsClosed  = new AtomicLong
    val maxOpenConnections = new AtomicLong
    val requestTimeouts    = new AtomicLong
    val idleTimeouts       = new AtomicLong

    @tailrec
    final def adjustMaxOpenConnections() {
      val co = connectionsOpened.get
      val cc = connectionsClosed.get
      val moc = maxOpenConnections.get
      val currentMoc = co - cc
      if (currentMoc > moc)
        if (!maxOpenConnections.compareAndSet(moc, currentMoc)) adjustMaxOpenConnections()
    }

    @tailrec
    final def adjustMaxOpenRequests() {
      val rqs = requestStarts.get
      val rss = responseStarts.get
      val mor = maxOpenRequests.get
      val currentMor = rqs - rss
      if (currentMor > mor)
        if (!maxOpenRequests.compareAndSet(mor, currentMor)) adjustMaxOpenRequests()
    }

    def toStats = HttpServer.Stats(
      uptime = Duration(System.currentTimeMillis - startTimestamp, TimeUnit.MILLISECONDS),
      totalRequests = requestStarts.get,
      openRequests = requestStarts.get - responseStarts.get,
      maxOpenRequests = maxOpenRequests.get,
      totalConnections = connectionsOpened.get,
      openConnections = connectionsOpened.get - connectionsClosed.get,
      maxOpenConnections = maxOpenConnections.get,
      requestTimeouts = requestTimeouts.get,
      idleTimeouts = idleTimeouts.get
    )

    def clear() {
      requestStarts.set(0L)
      responseStarts.set(0L)
      maxOpenRequests.set(0L)
      connectionsOpened.set(0L)
      connectionsClosed.set(0L)
      maxOpenConnections.set(0L)
      requestTimeouts.set(0L)
      idleTimeouts.set(0L)
    }
  }

  def apply(holder: StatsHolder): PipelineStage =
    new PipelineStage {
      def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines =
        new Pipelines {
          import holder._
          connectionsOpened.incrementAndGet()
          adjustMaxOpenConnections()

          val commandPipeline: CPL = {
            case x: HttpResponsePartRenderingContext if x.responsePart.isInstanceOf[HttpMessageStart] =>
              responseStarts.incrementAndGet()
              commandPL(x)

            case x: IOServer.Tell if x.message.isInstanceOf[Timeout] =>
              requestTimeouts.incrementAndGet()
              commandPL(x)

            case cmd => commandPL(cmd)
          }

          val eventPipeline: EPL = {
            case ev: HttpMessageStartEvent =>
              requestStarts.incrementAndGet()
              adjustMaxOpenRequests()
              eventPL(ev)

            case x: HttpServer.Closed =>
              connectionsClosed.incrementAndGet()
              if (x.reason == IdleTimeout) idleTimeouts.incrementAndGet()
              eventPL(x)

            case ev => eventPL(ev)
          }
        }
    }
}