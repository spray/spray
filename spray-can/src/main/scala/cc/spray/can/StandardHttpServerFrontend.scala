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
import akka.actor.ActorRef
import collection.mutable.Queue
import rendering.HttpResponsePartRenderingContext
import cc.spray.io.Pipelines

object StandardHttpServerFrontend {

  def apply(requestActorFactory: => ActorRef)(pipelines: Pipelines) = {
    val openRequests = Queue.empty[HttpRequest]
    pipelines.copy(
      upstream = {
        case part: HttpRequestPart =>
          part match {
            case x: HttpRequest         => openRequests += x
            case x: ChunkedRequestStart => openRequests += x.request
            case _                      =>
          }
          requestActorFactory ! part

        case event => pipelines.upstream(event)
      },
      downstream = {
        case part: HttpResponsePart => pipelines.downstream {
          if (openRequests.isEmpty) throw new IllegalStateException("Received ResponsePart for non-existing request")
          val request = part match {
            case _: HttpResponse      => openRequests.dequeue()
            case _: ChunkedMessageEnd => openRequests.dequeue()
            case _                    => openRequests.front
          }
          HttpResponsePartRenderingContext(part, request.method, request.protocol, request.connectionHeader)
        }
        case event => pipelines.downstream(event)
      }
    )
  }

}