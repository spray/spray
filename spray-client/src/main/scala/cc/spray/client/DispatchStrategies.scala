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
package client

import http._
import collection.mutable.Queue

trait DispatchStrategy {
  def dispatch(context: HttpRequestContext, conns: Seq[HttpConn])
  def onStateChange(conns: Seq[HttpConn])
}

trait HttpConn {
  // -1 -> unconnected
  def pendingResponses: Int
  def dispatch(context: HttpRequestContext)
}

trait HttpRequestContext {
  def request: HttpRequest
}

object DispatchStrategies {

  class NonPipelining extends DispatchStrategy {
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

    def findAvailableConnection(conns: Seq[HttpConn]) = {
      conns.find(_.pendingResponses == 0) orElse { // if possible dispatch to idle connections
        conns.find(_.pendingResponses == -1) // otherwise look for unconnected connections
      }
    }
  }

  object NonPipelining extends NonPipelining
}