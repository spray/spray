/*
 * Copyright (C) 2011-2013 spray.io
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

package spray.can
package server

import scala.annotation.tailrec
import scala.collection.immutable.Queue
import akka.io.Tcp
import spray.can.rendering.ResponsePartRenderingContext
import spray.http._
import spray.io._

object PipeliningLimiter {

  def apply(pipeliningLimit: Int): PipelineStage =
    new PipelineStage {
      require(pipeliningLimit > 0)

      def apply(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines =
        new Pipelines {
          var parkedRequestParts = Queue.empty[Http.MessageEvent]
          var openRequests = 0

          val commandPipeline: CPL = {
            case x: ResponsePartRenderingContext if x.responsePart.isInstanceOf[HttpMessageEnd] ⇒
              openRequests -= 1
              commandPL(x)
              if (parkedRequestParts.nonEmpty) {
                unparkOneRequest()
                if (parkedRequestParts.isEmpty) commandPL(Tcp.ResumeReading)
              }

            case cmd ⇒ commandPL(cmd)
          }

          val eventPipeline: EPL = {
            case ev: Http.MessageEvent ⇒
              if (openRequests < pipeliningLimit) {
                eventPL(ev)
                if (ev.ev.isInstanceOf[HttpMessageEnd]) openRequests += 1
              } else {
                commandPL(Tcp.SuspendReading)
                parkedRequestParts = parkedRequestParts enqueue ev
              }

            case ev ⇒ eventPL(ev)
          }

          @tailrec
          def unparkOneRequest(): Unit =
            if (!parkedRequestParts.isEmpty) {
              val next = parkedRequestParts.head
              parkedRequestParts = parkedRequestParts.tail
              eventPL(next)
              if (next.ev.isInstanceOf[HttpMessageEnd]) openRequests += 1
              else unparkOneRequest()
            }
        }
    }
}