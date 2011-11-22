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
      Pass.withTransform { _
        .withResponseTransformed(encoder.encode)
        .withRejectionsTransformed(_ + RejectionRejection(_.isInstanceOf[UnacceptedResponseEncodingRejection]))
      }
    } else Reject(UnacceptedResponseEncodingRejection(encoder.encoding))
  }
  
}
