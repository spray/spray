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

package spray.routing
package directives

import akka.actor.ActorRefFactory
import spray.http.{ HttpData, HttpEntity, HttpResponse }
import spray.httpx.marshalling.BasicMarshallers
import akka.util.ByteString

trait ChunkingDirectives {
  import BasicDirectives._

  /**
   * Converts unchunked HttpResponses coming back from its inner route into chunked responses of which each chunk
   * is smaller or equal to the given size, if the response entity is at least as large as the given threshold.
   * If the response content from the inner route is smaller than the given threshold the response is left untouched.
   */
  def autoChunk(csm: ChunkSizeMagnet): Directive0 = mapRequestContext { ctx ⇒
    import csm._
    ctx.withRouteResponseHandling {
      case HttpResponse(_, HttpEntity.NonEmpty(contentType, data), _, _) if csm selects data ⇒
        implicit val marshaller = BasicMarshallers.byteStringMarshaller(contentType)
        ctx.complete(chunkStream(data))
    }
  }

  /**
   * Converts unchunked HttpResponses coming back from its inner route into chunked responses of which each chunk
   * is smaller or equal to the given size, if the response entity contains HttpData.FileBytes and is at least as
   * large as the given threshold.
   * If the response content from the inner route is smaller than the given threshold the response is left untouched.
   */
  def autoChunkFileBytes(csm: ChunkSizeMagnet): Directive0 =
    autoChunk {
      new ChunkSizeMagnet {
        implicit def refFactory: ActorRefFactory = csm.refFactory
        def selects(data: HttpData): Boolean = data.hasFileBytes && csm.selects(data)
        def chunkStream(data: HttpData): Stream[ByteString] = csm.chunkStream(data)
      }
    }
}

object ChunkingDirectives extends ChunkingDirectives

abstract class ChunkSizeMagnet {
  implicit def refFactory: ActorRefFactory
  def selects(data: HttpData): Boolean
  def chunkStream(data: HttpData): Stream[ByteString]
}

object ChunkSizeMagnet {
  class Default(thresholdSize: Long, maxChunkSize: Int)(implicit val refFactory: ActorRefFactory) extends ChunkSizeMagnet {
    def selects(data: HttpData): Boolean = thresholdSize > 0 && data.length > thresholdSize
    def chunkStream(data: HttpData): Stream[ByteString] = data.toChunkStream(maxChunkSize)
  }
  implicit def fromInt(maxChunkSize: Int)(implicit factory: ActorRefFactory): ChunkSizeMagnet =
    new Default(maxChunkSize, maxChunkSize)
  implicit def fromLongAndInt(pair: (Long, Int))(implicit factory: ActorRefFactory): ChunkSizeMagnet =
    new Default(pair._1, pair._2)
  implicit def fromIntAndInt(pair: (Int, Int))(implicit factory: ActorRefFactory): ChunkSizeMagnet =
    new Default(pair._1, pair._2)
}