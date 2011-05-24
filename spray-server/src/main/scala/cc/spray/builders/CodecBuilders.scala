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
package builders

import http._
import HttpHeaders._
import utils.Product0
import org.parboiled.common.FileUtils
import java.io._
import java.util.zip._

private[spray] trait CodecBuilders {
  this: FilterBuilders =>

  def decodeRequest(decoder: Decoder) = filter { ctx =>
    if (ctx.request.content.isEmpty) {
      Pass()(_.cancelRejections[UnsupportedRequestEncodingRejection])
    } else if (ctx.request.encoding == decoder.encoding) {
      try {
        val decodedRequest = decoder.decode(ctx.request) 
        new Pass(Product0, _.withRequestTransformed(_ => decodedRequest))
      } catch {
        case e: Exception => Reject(CorruptRequestEncodingRejection(e.getMessage)) 
      }
    } else Reject(UnsupportedRequestEncodingRejection(decoder.encoding))
  }
  
  def encodeResponse(encoder: Encoder) = filter { ctx =>
    if (ctx.request.isEncodingAccepted(encoder.encoding)) {
      Pass() {
        _.withRoutingResultTransformed {
          case Respond(response) => Respond(encoder.encode(response))
          case Reject(rejections) => {
            Reject(rejections + RejectionRejection(_.isInstanceOf[UnacceptedResponseEncodingRejection]))
          }
        }
      }
    } else Reject(UnacceptedResponseEncodingRejection(encoder.encoding))
  }
  
}

trait Decoder {
  def encoding: HttpEncoding
  
  def decode(request: HttpRequest): HttpRequest = request.content match {
    case Some(content) => request.copy(
      content = Some(HttpContent(content.contentType, decodeBuffer(content.buffer)))
    )
    case _ => request
  }
  
  def decodeBuffer(buffer: Array[Byte]): Array[Byte]
  
  protected def copyBuffer(buffer: Array[Byte])(copy: (InputStream, OutputStream) => Unit) = {
    val in = new ByteArrayInputStream(buffer)
    val out = new ByteArrayOutputStream()
    copy(in, out)
    out.toByteArray
  }
} 

trait Encoder {
  def encoding: HttpEncoding
  
  def handle(response: HttpResponse): Boolean
  
  def encode(response: HttpResponse): HttpResponse = response.content match {
    case Some(content) if !response.isEncodingSpecified && handle(response) => response.copy(
      headers = `Content-Encoding`(encoding) :: response.headers,
      content = Some(HttpContent(content.contentType, encodeBuffer(content.buffer)))
    )
    case _ => response
  }

  def encodeBuffer(buffer: Array[Byte]): Array[Byte]
}

/**
 * An encoder and decoder for the HTTP 'identity' encoding.
 */
object NoEncoding extends Decoder with Encoder {
  val encoding = HttpEncodings.identity
  override def decode(request: HttpRequest) = request
  def decodeBuffer(buffer: Array[Byte]) = buffer
  def handle(response: HttpResponse) = false
  def encodeBuffer(buffer: Array[Byte]) = buffer
}

abstract class Gzip extends Decoder with Encoder {
  val encoding = HttpEncodings.gzip

  def decodeBuffer(buffer: Array[Byte]) = copyBuffer(buffer) { (in, out) => 
    FileUtils.copyAll(new GZIPInputStream(in), out)
  }

  def encodeBuffer(buffer: Array[Byte]) = copyBuffer(buffer) { (in, out) => 
    FileUtils.copyAll(in, new GZIPOutputStream(out))
  }
}

/**
 * An encoder and decoder for the HTTP 'gzip' encoding.
 */
object Gzip extends Gzip {
  def handle(response: HttpResponse) = response.isSuccess
  def apply(minContentSize: Int) = new Gzip {
    def handle(response: HttpResponse) = {
      response.isSuccess && response.content.get.buffer.length >= minContentSize
    }
  }
  def apply(predicate: HttpResponse => Boolean) = new Gzip {
    def handle(response: HttpResponse) = predicate(response)
  }
}

abstract class Deflate extends Decoder with Encoder {
  val encoding = HttpEncodings.deflate
  
  def decodeBuffer(buffer: Array[Byte]) = copyBuffer(buffer) { (in, out) => 
    FileUtils.copyAll(new DeflaterInputStream(in), out)
  }

  def encodeBuffer(buffer: Array[Byte]) = copyBuffer(buffer) { (in, out) => 
    FileUtils.copyAll(in, new DeflaterOutputStream(out))
  }
}

/**
 * An encoder and decoder for the HTTP 'deflate' encoding.
 */
object Deflate extends Deflate {
  def handle(response: HttpResponse) = response.isSuccess
  def apply(minContentSize: Int) = new Deflate {
    def handle(response: HttpResponse) = {
      response.isSuccess && response.content.get.buffer.length >= minContentSize
    }
  }
  def apply(predicate: HttpResponse => Boolean) = new Deflate {
    def handle(response: HttpResponse) = predicate(response)
  }
}