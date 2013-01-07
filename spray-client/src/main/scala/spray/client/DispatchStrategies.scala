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

package spray.client

import scala.collection.mutable


private[client] trait DispatchStrategies {
  this: HttpHostConnector =>

  trait DispatchStrategy {
    def dispatch(reqCtx: RequestContext)
    def onStateChange()
  }

  /**
   * Defines a DispatchStrategy with the following logic:
   *  - Dispatch to the first idle connection in the pool, if there is one.
   *  - If none is idle, dispatch to the first unconnected connection, if there is one.
   *  - If all are already connected, store the request and send it as soon as one
   *    connection becomes either idle or unconnected.
   */
  class NonPipelinedStrategy extends DispatchStrategy {
    val queue = mutable.Queue.empty[RequestContext]

    def dispatch(reqCtx: RequestContext) {
      findAvailableConnection match {
        case Some(conn) => conn.dispatch(reqCtx)
        case None => queue.enqueue(reqCtx)
      }
    }

    def onStateChange() {
      if (queue.nonEmpty)
        findAvailableConnection.foreach(_.dispatch(queue.dequeue()))
    }

    def findAvailableConnection: Option[HostConnection] =
      hostConnections.find(_.pendingResponses == 0) orElse { // if possible dispatch to idle connections
        hostConnections.find(_.pendingResponses == -1) // otherwise look for unconnected connections
      }
  }

  /**
   * Defines a DispatchStrategy with the following logic:
   *  - Dispatch to the first idle connection in the pool, if there is one.
   *  - If none is idle, dispatch to the first unconnected connection, if there is one.
   *  - If all are already connected, dispatch to the connection with the least open requests.
   */
  class PipelinedStrategy extends DispatchStrategy {
    def dispatch(reqCtx: RequestContext) {
      // if possible dispatch to idle connections, if no idle ones are available prefer
      // unconnected connections over busy ones and less busy ones over more busy ones
      val conn = hostConnections.find(_.pendingResponses == 0).getOrElse(hostConnections.minBy(_.pendingResponses))
      conn.dispatch(reqCtx)
    }

    def onStateChange() {
      // nothing to do here
    }
  }
}