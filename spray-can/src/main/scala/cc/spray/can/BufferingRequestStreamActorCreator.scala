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

package cc.spray.can

import akka.actor.{ActorRef, Actor}

class BufferingRequestStreamActorCreator(serviceActor: ActorRef, maxContentLength: Int)
        extends (ChunkedRequestContext => Actor) {

  def apply(context: ChunkedRequestContext) = new Actor {
    var body: Array[Byte] = _
    var totalBytes = 0L

    protected def receive = {
      case x: MessageChunk => body match {
        case null =>
          body = x.body
          totalBytes = body.length
        case _ if body.length + x.body.length <= maxContentLength =>
          body = body concat x.body
          totalBytes = body.length
        case _ => become {
          case x: MessageChunk => totalBytes += x.body.length
          case x: ChunkedRequestEnd => x.responder.complete {
            HttpResponse(
              status = 413, // Request Entity too large
              headers = List(HttpHeader("Content-Type", "text/plain")),
              body = ("The aggregated content length of " + totalBytes +
                      " exceeds the configured limit of this server").getBytes("ISO-8859-1")
            )
          }
        }
      }
      case x: ChunkedRequestEnd => serviceActor ! RequestContext(
        request = HttpRequest(
          method = context.requestStart.method,
          uri = context.requestStart.uri,
          headers = context.requestStart.headers,
          body = body
        ),
        remoteAddress = context.remoteAddress,
        x.responder
      )
    }
  }
}