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

import util.make
import typeconversion.DefaultMarshallers
import akka.actor.ActorSystem

private[spray] trait ChunkingDirectives {
  this: BasicDirectives =>

  implicit def actorSystem: ActorSystem

  /**
   * Automatically converts a non-rejected response from its inner route into a chunked response of which each chunk
   * (save the very last) has the given size.
   * If the response content from the inner route is smaller than chunkSize a "regular", unchunked response is produced.
   */
  def autoChunk(chunkSize: Int) = transformRequestContext { ctx =>
    import DefaultMarshallers.{ByteArrayMarshaller => _, _}
    ctx.withResponderTransformed { responder =>
      responder.withComplete { response =>
        response.content match {
          case Some(content) if content.buffer.length > chunkSize => {
            def split(ix: Int): Stream[Array[Byte]] = {
              def chunkBuf(size: Int) = make(new Array[Byte](size)){ System.arraycopy(content.buffer, ix, _, 0, size) }
              if (ix < content.buffer.length - chunkSize) Stream.cons(chunkBuf(chunkSize), split(ix + chunkSize))
              else Stream.cons(chunkBuf(content.buffer.length - ix), Stream.Empty)
            }
            implicit val byteArrayMarshaller = DefaultMarshallers.byteArrayMarshaller(content.contentType)
            ctx.complete(split(0))
          }
          case _ => responder.complete(response)
        }
      }
    }
  }

}
