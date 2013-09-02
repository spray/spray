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

import spray.util._
import spray.http._
import spray.httpx.encoding._

trait EncodingDirectives {
  import BasicDirectives._
  import MiscDirectives._
  import RouteDirectives._

  /**
   * Wraps its inner Route with decoding support using the given Decoder.
   */
  def decodeRequest(decoder: Decoder): Directive0 = {
    def applyDecoder = mapInnerRoute { inner ⇒
      ctx ⇒
        tryToEither(decoder.decode(ctx.request)) match {
          case Right(decodedRequest) ⇒ inner(ctx.copy(request = decodedRequest))
          case Left(error)           ⇒ ctx.reject(CorruptRequestEncodingRejection(error.getMessage.nullAsEmpty))
        }
    }
    requestEntityEmpty | (
      requestEncodedWith(decoder.encoding) &
      applyDecoder &
      cancelAllRejections(ofTypes(classOf[UnsupportedRequestEncodingRejection], classOf[CorruptRequestEncodingRejection])))
  }

  /**
   * Rejects the request with an UnsupportedRequestEncodingRejection if its encoding doesn't match the given one.
   */
  def requestEncodedWith(encoding: HttpEncoding): Directive0 =
    extract(_.request.encoding).flatMap {
      case `encoding` ⇒ pass
      case _          ⇒ reject(UnsupportedRequestEncodingRejection(encoding))
    }

  /**
   * Wraps its inner Route with encoding support using the given Encoder.
   */
  def encodeResponse(encoder: Encoder) = {
    def applyEncoder = mapRequestContext { ctx ⇒
      @volatile var compressor: Compressor = null
      ctx.withHttpResponsePartMultiplied {
        case response: HttpResponse ⇒ encoder.encode(response) :: Nil
        case x @ ChunkedResponseStart(response) ⇒ encoder.startEncoding(response) match {
          case Some((compressedResponse, c)) ⇒
            compressor = c
            ChunkedResponseStart(compressedResponse) :: Nil
          case None ⇒ x :: Nil
        }
        case MessageChunk(body, exts) if compressor != null ⇒
          MessageChunk(compressor.compress(body).flush(), exts) :: Nil
        case x: ChunkedMessageEnd if compressor != null ⇒
          val body = compressor.finish()
          if (body.length > 0) MessageChunk(body) :: x :: Nil else x :: Nil
        case x ⇒ x :: Nil
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
  def responseEncodingAccepted(encoding: HttpEncoding): Directive0 =
    extract(_.request.isEncodingAccepted(encoding))
      .flatMap(if (_) pass else reject(UnacceptedResponseEncodingRejection(encoding)))

  /**
   * Wraps its inner Route with response compression, only falling back to
   * uncompressed responses if the client specifically requests the "identity"
   * encoding and preferring Gzip over Deflate.
   */
  def compressResponse: Directive0 = compressResponseWith(Gzip, Deflate, NoEncoding)

  /**
   * Wraps its inner Route with response compression if and only if the client
   * specifically requests compression with an Accept-Encoding header.
   */
  def compressResponseIfRequested: Directive0 = compressResponseWith(NoEncoding, Gzip, Deflate)

  /**
   * Wraps its inner Route with response compression, using the specified
   * encoders in order of preference.
   */
  def compressResponseWith(first: Encoder, more: Encoder*): Directive0 =
    if (more.isEmpty) encodeResponse(first)
    else more.foldLeft(encodeResponse(first)) { (r, encoder) ⇒ r | encodeResponse(encoder) }

  /**
   * Wraps its inner Route with request decompression, assuming
   * Gzip compressed requests but falling back to Deflate or no
   * compression if the request contains the relevant Content-Encoding
   * header.
   */
  def decompressRequest: Directive0 = decompressRequestWith(Gzip, Deflate, NoEncoding)

  /**
   * Wraps its inner Route with request decompression, trying the specified
   * decoders in turn.
   */
  def decompressRequestWith(first: Decoder, more: Decoder*): Directive0 =
    if (more.isEmpty) decodeRequest(first)
    else more.foldLeft(decodeRequest(first)) { (r, decoder) ⇒ r | decodeRequest(decoder) }
}

object EncodingDirectives extends EncodingDirectives
