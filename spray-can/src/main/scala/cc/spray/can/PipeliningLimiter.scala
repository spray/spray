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
import collection.mutable.Queue
import model.{HttpMessageEndPart, HttpRequestPart}
import rendering.HttpResponsePartRenderingContext
import annotation.tailrec


object PipeliningLimiter {

  def apply(pipeliningLimit: Int) = new DoublePipelineStage {
    require(pipeliningLimit > 0)

    def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines = new Pipelines {
      var parkedRequestParts: Queue[HttpRequestPart] = _
      var openRequests = 0
      var limit = pipeliningLimit
      var readingStopped = false

      def commandPipeline(command: Command) {
        command match {
          case x: HttpResponsePartRenderingContext if x.responsePart.isInstanceOf[HttpMessageEndPart] =>
            openRequests -= 1
            commandPL(command)
            if (parkedRequestParts != null && !parkedRequestParts.isEmpty) {
              unparkOneRequest()
              if (parkedRequestParts.isEmpty) resumeReading()
            }

          case _ => commandPL(command)
        }
      }

      def eventPipeline(event: Event) {
        event match {
          case x: HttpRequestPart =>
            if (openRequests == limit) {
              stopReading()
              park(x)
            } else {
              if (x.isInstanceOf[HttpMessageEndPart]) openRequests += 1
              eventPL(event)
            }

          case _ => eventPL(event)
        }
      }

      def stopReading() {
        if (!readingStopped) {
          readingStopped = true
          commandPL(IoServer.StopReading)
        }
      }

      def resumeReading() {
        if (readingStopped) {
          readingStopped = false
          commandPL(IoServer.ResumeReading)
        }
      }

      def park(part: HttpRequestPart) {
        if (parkedRequestParts == null) parkedRequestParts = Queue(part)
        else parkedRequestParts.enqueue(part)
      }

      @tailrec
      def unparkOneRequest() {
        if (!parkedRequestParts.isEmpty) {
          parkedRequestParts.dequeue() match {
            case part: HttpMessageEndPart =>
              openRequests += 1
              eventPL(part)
            case part =>
              eventPL(part)
              unparkOneRequest()
          }
        }
      }
    }
  }

}