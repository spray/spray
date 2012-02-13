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

package cc.spray.can

import model._
import nio._
import collection.mutable.Queue
import java.nio.channels.SocketChannel
import akka.actor.{UntypedChannel, Channel}
import util.Logging
import cc.spray.io.Pipelines

object StandardHttpClientFrontend extends Logging {

  def apply(pipelines: Pipelines) = {
    val openRequests = Queue.empty[(HttpRequest, UntypedChannel)]
    pipelines.copy(
      upstream = {
        case part: HttpResponsePart =>
          if (!openRequests.isEmpty) {
            val (request, channel) = part match {
              case _: HttpResponse      => openRequests.dequeue()
              case _: ChunkedMessageEnd => openRequests.dequeue()
              case _                    => openRequests.front
            }
            channel.!(request)(pipelines.handle.handler)
          } else log.warn("Received ResponsePart for non-existing request: {}", part)

        case event => pipelines.upstream(event)
      },
      downstream = {
        case nio.Connected(_, channel: UntypedChannel) =>
          val handle = pipelines.handle
          channel ! Connected(handle.handler, handle.key.channel.asInstanceOf[SocketChannel].socket.getInetAddress)

        case part: HttpRequestPart =>
          part match {
            case x: HttpRequest         => openRequests += x -> pipelines.handle.handler.channel
            case x: ChunkedRequestStart => openRequests += x.request -> pipelines.handle.handler.channel
            case _                      =>
          }
          pipelines.downstream(part)
      }
    )
  }

}