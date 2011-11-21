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
package directives

import akka.actor.{PoisonPill, Actor}
import http.{HttpResponse, MessageChunk, HttpContent}
import utils.make

private[spray] trait ChunkingDirectives {
  this: BasicDirectives =>

  /**
   * Automatically converts a non-rejected response from its inner route into a response with HTTP transfer encoding
   * "chunked", of which each chunk (save the very last) has the given size.
   * If the response content from the inner route is smaller than chunkSize a "regular", unchunked response is produced.
   */
  def autoChunk(chunkSize: Int) = transformRequestContext { ctx =>
    def sendChunked(response: HttpResponse, content: HttpContent) {
      def split(ix: Int): Stream[Array[Byte]] = {
        def chunkBuf(size: Int) = make(new Array[Byte](size)) {
          System.arraycopy(content.buffer, ix, _, 0, size)
        }
        if (ix < content.buffer.length - chunkSize)
          Stream.cons(chunkBuf(chunkSize), split(ix + chunkSize))
        else
          Stream.cons(chunkBuf(content.buffer.length - ix), Stream.Empty)
      }
      val first #:: rest = split(0)
      val chunkedResponder = ctx.responder.startChunkedResponse {
        response.withContent(Some(content.withBuffer(first))) // response with initial chunk
      }
      Actor.actorOf(new Actor() {
        def receive = { case chunk #:: remaining =>
          // we only send the next chunk when the previous has actually gone out
          chunkedResponder.onChunkSent { _ =>
            self ! {
              if (remaining.isEmpty) {
                chunkedResponder.close()
                PoisonPill
              } else remaining
            }
          }
          chunkedResponder.sendChunk(MessageChunk(chunk.asInstanceOf[Array[Byte]]))
        }
      }).start() ! rest
    }

    ctx.withResponder {
      ctx.responder.withReply {
        case x: Reject => ctx.responder.reply(x)
        case x: Respond => x.response.content match {
          case Some(content) if content.buffer.length > chunkSize => sendChunked(x.response, content)
          case _ => ctx.responder.reply(x)
        }
      }
    }
  }

}
