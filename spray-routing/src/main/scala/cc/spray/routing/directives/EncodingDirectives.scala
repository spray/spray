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

import spray.util._
import spray.http._
import spray.httpx.encoding._


trait EncodingDirectives {
  import BasicDirectives._
  import MiscDirectives._

  /**
   * Wraps its inner Route with decoding support using the given Decoder.
   */
  def decodeRequest(decoder: Decoder): Directive0 = {
    def applyDecoder = mapInnerRoute { inner => ctx =>
      tryToEither(decoder.decode(ctx.request)) match {
        case Right(decodedRequest) => inner(ctx.copy(request = decodedRequest))
        case Left(error) => ctx.reject(CorruptRequestEncodingRejection(error.getMessage))
      }
    }
    requestEntityEmpty | (
      requestEncodedWith(decoder.encoding) &
      applyDecoder &
      cancelAllRejections(ofType[UnsupportedRequestEncodingRejection])
    )
  }

  /**
   * Rejects the request with an UnsupportedRequestEncodingRejection if its encoding doesn't match the given one.
   */
  def requestEncodedWith(encoding: HttpEncoding): Directive0 = filter { ctx =>
    if (ctx.request.encoding == encoding) Pass.Empty
    else Reject(UnsupportedRequestEncodingRejection(encoding))
  }

  /**
   * Wraps its inner Route with encoding support using the given Encoder.
   */
  def encodeResponse(encoder: Encoder) = {
    def applyEncoder = mapRequestContext { ctx =>
      @volatile var compressor: Compressor = null
      ctx.flatMapHttpResponsePartResponse {
        case response: HttpResponse => encoder.encode(response) :: Nil
        case x@ ChunkedResponseStart(response) => encoder.startEncoding(response) match {
          case Some((compressedResponse, c)) =>
            compressor = c
            ChunkedResponseStart(compressedResponse) :: Nil
          case None => x :: Nil
        }
        case MessageChunk(body, exts) if compressor != null =>
          MessageChunk(compressor.compress(body).flush(), exts) :: Nil
        case x: ChunkedMessageEnd if compressor != null =>
          val body = compressor.finish()
          if (body.length > 0) MessageChunk(body) :: x :: Nil else x :: Nil
        case x => x :: Nil
      }
    }
    responseEncodingAccepted(encoder.encoding) &
    applyEncoder &
    cancelAllRejections(ofType[UnacceptedResponseEncodingRejection])
  }

  /**
   * Rejects the request with an UnacceptedResponseEncodingRejection
   * if the given encoding is not accepted for the response.
   */
  def responseEncodingAccepted(encoding: HttpEncoding): Directive0 = filter { ctx =>
    if (ctx.request.isEncodingAccepted(encoding)) Pass.Empty
    else Reject(UnacceptedResponseEncodingRejection(encoding))
  }

}

object EncodingDirectives extends EncodingDirectives