/*
 * Copyright (C) 2011-2012 spray.cc
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

package cc.spray.routing
package directives

import akka.actor.ActorSystem
import cc.spray.http.{HttpBody, HttpResponse}
import cc.spray.httpx.marshalling.Marshaller
import cc.spray.util._


trait ChunkingDirectives {
  import BasicDirectives._

  def system: ActorSystem

  /**
   * Automatically converts a non-rejected response from its inner route into a chunked response of which each chunk
   * (save the very last) has the given size.
   * If the response content from the inner route is smaller than chunkSize a "regular", unchunked response is produced.
   */
  def autoChunk(chunkSize: Int) = transformRequestContext { ctx =>
    ctx.withRouteResponseHandling {
      case HttpResponse(_, HttpBody(contentType, buffer), _, _) if buffer.length > chunkSize =>
        def split(ix: Int): Stream[Array[Byte]] = {
          def chunkBuf(size: Int) = make(new Array[Byte](size)) {
            System.arraycopy(buffer, ix, _, 0, size)
          }
          if (ix < buffer.length - chunkSize) Stream.cons(chunkBuf(chunkSize), split(ix + chunkSize))
          else Stream.cons(chunkBuf(buffer.length - ix), Stream.Empty)
        }
        implicit val byteArrayMarshaller = Marshaller.delegate[Array[Byte], Array[Byte]](contentType)(identityFunc[Array[Byte]])
        implicit val refFactory = system
        ctx.complete(split(0))
    }
  }

}
