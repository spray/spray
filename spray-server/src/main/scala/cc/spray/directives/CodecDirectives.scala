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

import encoding._
import typeconversion.ChunkSender
import http.{MessageChunk, HttpHeader, ChunkExtension}

private[spray] trait CodecDirectives {
  this: BasicDirectives =>

  /**
   * Wraps its inner Route with decoding support using the given Decoder.
   */
  def decodeRequest(decoder: Decoder) = filter { ctx =>
    if (ctx.request.content.isEmpty) {
      Pass.withTransform(_.cancelRejectionsOfType[UnsupportedRequestEncodingRejection])
    } else if (ctx.request.encoding == decoder.encoding) {
      try {
        val decodedRequest = decoder.decode(ctx.request) 
        Pass.withTransform { _
           .cancelRejectionsOfType[UnsupportedRequestEncodingRejection]
           .withRequestTransformed(_ => decodedRequest)
        }
      } catch {
        case e: Exception => Reject(CorruptRequestEncodingRejection(e.getMessage)) 
      }
    } else Reject(UnsupportedRequestEncodingRejection(decoder.encoding))
  }

  /**
   * Wraps its inner Route with encoding support using the given Encoder.
   */
  def encodeResponse(encoder: Encoder) = filter { ctx =>
    if (ctx.request.isEncodingAccepted(encoder.encoding)) {
      Pass.withTransform {
        _.withResponderTransformed { responder =>
          RequestResponder(
            complete = response => responder.complete(encoder.encode(response)),
            reject = rejections =>
              responder.reject(rejections + RejectionRejection(_.isInstanceOf[UnacceptedResponseEncodingRejection])),
            startChunkedResponse = { response =>
              encoder.startEncoding(response) match {
                case Some((compressedResponse, compressor)) =>
                  val inner = responder.startChunkedResponse(compressedResponse)
                  new ChunkSender {
                    def sendChunk(chunk: MessageChunk) = {
                      inner.sendChunk(chunk.copy(body = compressor.compress(chunk.body).flush()))
                    }
                    def close(extensions: List[ChunkExtension], trailer: List[HttpHeader]) {
                      val body = compressor.finish()
                      if (body.length > 0) inner.sendChunk(MessageChunk(body))
                      inner.close(extensions, trailer)
                    }
                  }
                case None => responder.startChunkedResponse(response)
              }
            }
          )
        }
      }
    } else Reject(UnacceptedResponseEncodingRejection(encoder.encoding))
  }

}
