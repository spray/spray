/*
 * Copyright (C) 2011, 2012 Mathias Doenitz
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
package can

import io._
import model.HttpMessageStartPart
import rendering.HttpResponsePartRenderingContext
import can.HttpServer.RequestTimeout
import java.util.concurrent.atomic.AtomicLong


object StatsSupport {

  def apply() = new DoublePipelineStage {
    val requestStarts      = new AtomicLong
    val responseStarts     = new AtomicLong
    val maxOpenRequests    = new AtomicLong
    val connectionsOpened  = new AtomicLong
    val connectionsClosed  = new AtomicLong
    val maxOpenConnections = new AtomicLong
    val requestTimeouts    = new AtomicLong
    val idleTimeouts       = new AtomicLong

    def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines = new Pipelines {
      {
        val co = connectionsOpened.incrementAndGet()
        val cc = connectionsClosed.get
        val moc = maxOpenConnections.get
        if (co - cc > moc) maxOpenConnections.compareAndSet(moc, co - cc)
      }

      def commandPipeline(command: Command) {
        command match {
          case x: HttpResponsePartRenderingContext if x.responsePart.isInstanceOf[HttpMessageStartPart] =>
            responseStarts.incrementAndGet()
            commandPL(command)

          case x: IoServer.Tell if x.message.isInstanceOf[RequestTimeout] =>
            requestTimeouts.incrementAndGet()
            commandPL(command)

          case HttpServer.GetStats => commandPL {
            IoServer.Tell(
              receiver = context.connectionActorContext.sender,
              message = HttpServer.Stats(
                totalRequests = requestStarts.get,
                openRequests = requestStarts.get - responseStarts.get,
                maxOpenRequests = maxOpenRequests.get,
                totalConnections = connectionsOpened.get,
                openConnections = connectionsOpened.get - connectionsClosed.get,
                maxOpenConnections = maxOpenConnections.get,
                requestTimeouts = requestTimeouts.get,
                idleTimeouts = idleTimeouts.get
              ),
              sender = context.self
            )
          }
          case _ => commandPL(command)
        }
      }

      def eventPipeline(event: Event) {
        event match {
          case _: HttpMessageStartPart =>
            val rqs = requestStarts.incrementAndGet()
            val rss = responseStarts.get
            val mor = maxOpenRequests.get
            if (rqs - rss > mor) maxOpenRequests.compareAndSet(mor, rqs - rss)

          case x: HttpServer.Closed =>
            connectionsClosed.incrementAndGet()
            if (x.reason == IdleTimeout) idleTimeouts.incrementAndGet()

          case _ =>
        }
        eventPL(event)
      }
    }
  }

}