/*
 * Copyright © 2011-2013 the spray project <http://spray.io>
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
import spray.httpx.marshalling.{ ToResponseMarshaller, BasicMarshallers }

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
      case HttpResponse(status, HttpEntity.NonEmpty(contentType, data), headers, _) if csm selects data ⇒
        implicit val marshaller = BasicMarshallers.httpDataMarshaller(contentType)
        ctx.complete(chunkStream(data))(ToResponseMarshaller.fromMarshaller(status, headers))
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
        def chunkStream(data: HttpData): Stream[HttpData] = csm.chunkStream(data)
      }
    }
}

object ChunkingDirectives extends ChunkingDirectives

abstract class ChunkSizeMagnet {
  implicit def refFactory: ActorRefFactory
  def selects(data: HttpData): Boolean
  def chunkStream(data: HttpData): Stream[HttpData]
}

object ChunkSizeMagnet {
  class Default(thresholdSize: Long, maxChunkSize: Long)(implicit val refFactory: ActorRefFactory) extends ChunkSizeMagnet {
    def selects(data: HttpData): Boolean = thresholdSize > 0 && data.length > thresholdSize
    def chunkStream(data: HttpData): Stream[HttpData] = data.toChunkStream(maxChunkSize)
  }
  implicit def fromChunkSize[A](maxChunkSize: A)(implicit factory: ActorRefFactory, aView: A ⇒ Long): ChunkSizeMagnet = {
    val size = aView(maxChunkSize)
    new Default(size, size)
  }
  implicit def fromPair[A, B](pair: (A, B))(implicit factory: ActorRefFactory, aView: A ⇒ Long, bView: B ⇒ Long): ChunkSizeMagnet =
    new Default(aView(pair._1), bView(pair._2))
}
