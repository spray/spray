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
import spray.http.{ HttpEntity, HttpResponse }
import spray.httpx.marshalling.BasicMarshallers

trait ChunkingDirectives {
  import BasicDirectives._

  /**
   * Automatically converts non-rejected responses from its inner route into chunked responses of which each chunk
   * (save the very last) has the given size.
   * If the response content from the inner route is smaller than chunkSize a "regular", unchunked response is produced.
   */
  def autoChunk(csm: ChunkSizeMagnet) = mapRequestContext { ctx ⇒
    import csm._
    ctx.withRouteResponseHandling {
      case HttpResponse(_, HttpEntity.NonEmpty(contentType, data), _, _) if data.length > chunkSize ⇒
        def split(ix: Long = 0L): Stream[Array[Byte]] = {
          def chunkBuf(size: Int) = {
            val array = new Array[Byte](size)
            data.copyToArray(array, sourceOffset = ix, span = size)
            array
          }
          if (ix < data.length - chunkSize) Stream.cons(chunkBuf(chunkSize), split(ix + chunkSize))
          else Stream.cons(chunkBuf((data.length - ix).toInt), Stream.Empty)
        }
        implicit val marshaller = BasicMarshallers.byteArrayMarshaller(contentType)
        ctx.complete(split())
    }
  }
}

object ChunkingDirectives extends ChunkingDirectives

class ChunkSizeMagnet(val chunkSize: Int)(implicit val refFactory: ActorRefFactory)

object ChunkSizeMagnet {
  implicit def fromInt(chunkSize: Int)(implicit factory: ActorRefFactory) =
    new ChunkSizeMagnet(chunkSize)
}