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

package spray
package client

import collection.mutable.Queue
import http._


/**
 * Abstraction over the logic of how to dispatch outgoing requests to one of several connections.
 */
trait DispatchStrategy {
  /**
   * Requests that the given context be dispatched to one of the given HttpConns, if possible.
   * The strategy might also be to not dispatch the request at all and save it for a dispatch at a later time.
   */
  def dispatch(context: HttpRequestContext, conns: Seq[HttpConn])

  /**
   * Informs the strategy logic of a change in state of the given connections.
   */
  def onStateChange(conns: Seq[HttpConn])
}

/**
 * Abstraction over HTTP connections in the context of a [[spray.client.DispatchStrategy]]
 */
trait HttpConn {
  /**
   * Returns the number of open requests on this connection.
   * If the connection is unconnected the method returns -1.
   */
  def pendingResponses: Int

  /**
   * Dispatches the given request context to this connection.
   */
  def dispatch(context: HttpRequestContext)
}

trait HttpRequestContext {
  def request: HttpRequest
}

object DispatchStrategies {

  /**
   * Defines a [[spray.client.DispatchStrategy]] with the following logic:
   *  - Dispatch to the first idle connection in the pool, if there is one.
   *  - If none is idle, dispatch to the first unconnected connection, if there is one.
   *  - If all are already connected, store the request and send it as soon as one
   *    connection becomes either idle or unconnected.
   */
  class NonPipelined extends DispatchStrategy {
    val queue = Queue.empty[HttpRequestContext]

    def dispatch(context: HttpRequestContext, conns: Seq[HttpConn]) {
      findAvailableConnection(conns) match {
        case Some(conn) => conn.dispatch(context)
        case None => queue.enqueue(context)
      }
    }

    def onStateChange(conns: Seq[HttpConn]) {
      if (queue.nonEmpty) {
        findAvailableConnection(conns).foreach(_.dispatch(queue.dequeue()))
      }
    }

    def findAvailableConnection(conns: Seq[HttpConn]): Option[HttpConn] = {
      conns.find(_.pendingResponses == 0) orElse { // if possible dispatch to idle connections
        conns.find(_.pendingResponses == -1) // otherwise look for unconnected connections
      }
    }
  }

  object NonPipelined {
    def apply() = new NonPipelined
  }

  /**
   * Defines a [[spray.client.DispatchStrategy]] with the following logic:
   *  - Dispatch to the first idle connection in the pool, if there is one.
   *  - If none is idle, dispatch to the first unconnected connection, if there is one.
   *  - If all are already connected, dispatch to the connection with the least open requests.
   */
  class Pipelined extends DispatchStrategy {
    def dispatch(context: HttpRequestContext, conns: Seq[HttpConn]) {
      // if possible dispatch to idle connections, if no idle ones are available prefer
      // unconnected connections over busy ones and less busy ones over more busy ones
      val conn = conns.find(_.pendingResponses == 0).getOrElse(conns.minBy(_.pendingResponses))
      conn.dispatch(context)
    }

    def onStateChange(conns: Seq[HttpConn]) {
      // nothing to do here
    }
  }

  object Pipelined extends Pipelined
}