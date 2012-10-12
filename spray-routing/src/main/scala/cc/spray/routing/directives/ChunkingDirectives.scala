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

package spray.routing
package directives

import akka.actor.ActorRefFactory
import spray.http.{HttpBody, HttpResponse}
import spray.httpx.marshalling.BasicMarshallers
import spray.util._


trait ChunkingDirectives {
  import BasicDirectives._

  /**
   * Automatically converts non-rejected responses from its inner route into chunked responses of which each chunk
   * (save the very last) has the given size.
   * If the response content from the inner route is smaller than chunkSize a "regular", unchunked response is produced.
   */
  def autoChunk(csm: ChunkSizeMagnet) = mapRequestContext { ctx =>
    import csm._
    ctx.withRouteResponseHandling {
      case HttpResponse(_, HttpBody(contentType, buffer), _, _) if buffer.length > chunkSize =>
        def split(ix: Int): Stream[Array[Byte]] = {
          def chunkBuf(size: Int) = make(new Array[Byte](size)) {
            System.arraycopy(buffer, ix, _, 0, size)
          }
          if (ix < buffer.length - chunkSize) Stream.cons(chunkBuf(chunkSize), split(ix + chunkSize))
          else Stream.cons(chunkBuf(buffer.length - ix), Stream.Empty)
        }
        implicit val bufferMarshaller = BasicMarshallers.byteArrayMarshaller(contentType)
        ctx.complete(split(0))
    }
  }

}

object ChunkingDirectives extends ChunkingDirectives


trait ChunkSizeMagnet{
  def chunkSize: Int
  implicit def refFactory: ActorRefFactory
}

object ChunkSizeMagnet {
  implicit def fromInt(size: Int)(implicit factory: ActorRefFactory) = new ChunkSizeMagnet {
    def chunkSize = size
    def refFactory = factory
  }
}