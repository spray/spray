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

import collection.mutable
import annotation.tailrec
import spray.can.rendering.HttpResponsePartRenderingContext
import spray.can.HttpEvent
import spray.http._
import spray.io._


object PipeliningLimiter {

  def apply(pipeliningLimit: Int): PipelineStage =
    new PipelineStage {
      require(pipeliningLimit > 0)

      def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines =
        new Pipelines {
          var parkedRequestParts: mutable.Queue[HttpRequestPart] = _
          var openRequests = 0
          var limit = pipeliningLimit
          var readingStopped = false

          val commandPipeline: CPL = {
            case x: HttpResponsePartRenderingContext if x.responsePart.isInstanceOf[HttpMessageEnd] =>
              openRequests -= 1
              commandPL(x)
              if (parkedRequestParts != null && !parkedRequestParts.isEmpty) {
                unparkOneRequest()
                if (parkedRequestParts.isEmpty) resumeReading()
              }

            case cmd => commandPL(cmd)
          }

          val eventPipeline: EPL = {
            case ev@ HttpEvent(x: HttpRequestPart) =>
              if (openRequests == limit) {
                stopReading()
                park(x)
              } else {
                if (x.isInstanceOf[HttpMessageEnd]) openRequests += 1
                eventPL(ev)
              }

            case ev => eventPL(ev)
          }

          def stopReading() {
            if (!readingStopped) {
              readingStopped = true
              commandPL(IOServer.StopReading)
            }
          }

          def resumeReading() {
            if (readingStopped) {
              readingStopped = false
              commandPL(IOServer.ResumeReading)
            }
          }

          def park(part: HttpRequestPart) {
            if (parkedRequestParts == null) parkedRequestParts = mutable.Queue(part)
            else parkedRequestParts.enqueue(part)
          }

          @tailrec
          def unparkOneRequest() {
            if (!parkedRequestParts.isEmpty) {
              parkedRequestParts.dequeue() match {
                case part: HttpMessageEnd =>
                  openRequests += 1
                  eventPL(HttpEvent(part))
                case part =>
                  eventPL(HttpEvent(part))
                  unparkOneRequest()
              }
            }
          }
        }
    }
}