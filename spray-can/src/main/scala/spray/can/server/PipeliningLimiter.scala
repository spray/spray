/*
 * Copyright © 2011-2015 the spray project <http://spray.io>
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
import RequestParsing._

/**
 * The PipeliningLimiter tries to limit how much work an external requester can
 * produce by sending lots of pipelined request directly after each other. It works
 * by only permitting a limited number of open requests after which 1. incoming data
 * flow is stopped and 2. incoming requests which are already parsed are put into a queue.
 *
 * Some things to keep in mind:
 *   * It may take quite a while until Tcp.SuspendReading gets to the networking part. In
 *     this time quite a bit of data may already be in queues to be processed.
 *   * It doesn't work at all with fast-path because all the request processing is done instantly,
 *     i.e. during the `eventPL` call in handleEvent the complete request is handled and already sent
 *     out so that the openRequests counter is never increased.
 */
private object PipeliningLimiter {

  def apply(pipeliningLimit: Int): PipelineStage =
    new PipelineStage {
      require(pipeliningLimit > 0)

      def apply(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines =
        new Pipelines {
          var parkedRequestParts = Queue.empty[Event]
          var openRequests = 0

          def commandPipeline: CPL = commandPL
          val eventPipeline: EPL = {
            case ev @ HttpMessageStartEvent(part, _) ⇒ handleRequestPart(ev, part)
            case ev @ Http.MessageEvent(part)        ⇒ handleRequestPart(ev, part)
            case ev: AckEventWithReceiver            ⇒ handleResponseAck(ev)
            case ev                                  ⇒ eventPL(ev)
          }

          def handleRequestPart(ev: Event, part: HttpMessagePart): Unit =
            if (openRequests < pipeliningLimit) {
              if (part.isInstanceOf[HttpMessageEnd]) openRequests += 1
              eventPL(ev)
            } else {
              commandPL(Tcp.SuspendReading)
              parkedRequestParts = parkedRequestParts enqueue ev
            }
          def handleResponseAck(ev: AckEventWithReceiver): Unit = {
            openRequests -= 1
            eventPL(ev)
            if (parkedRequestParts.nonEmpty) {
              unparkOneRequest()
              if (parkedRequestParts.isEmpty) commandPL(Tcp.ResumeReading)
            }
          }

          @tailrec
          def unparkOneRequest(): Unit =
            if (!parkedRequestParts.isEmpty) {
              val next = parkedRequestParts.head
              parkedRequestParts = parkedRequestParts.tail

              eventPipeline(next)
              if (openRequests < pipeliningLimit) unparkOneRequest()
            }
        }
    }
}
