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
package cc.spray.can

import java.nio.ByteBuffer
import annotation.tailrec
import utils.DateTime
import java.nio.charset.Charset
import HttpProtocols._

trait ResponsePreparer {

  private[can] val US_ASCII = Charset.forName("US-ASCII")
  private val HttpVersionPlusSP = "HTTP/1.1 ".getBytes(US_ASCII)
  private val ColonSP = ": ".getBytes(US_ASCII)
  private val CRLF = "\r\n".getBytes(US_ASCII)
  private val SingleSP = " ".getBytes(US_ASCII)
  private val StatusLine200 = "HTTP/1.1 200 OK\r\n".getBytes(US_ASCII)

  protected def prepare(response: HttpResponse, reqProtocol: HttpProtocol,
                        reqConnectionHeader: Option[String]): RawResponse = {
    import ByteBuffer._

    def statusLine(rawResponse: RawResponse) = rawResponse.copy(buffers = {
      response.status match {
        case 200 => wrap(StatusLine200) :: rawResponse.buffers
        case x => wrap(HttpVersionPlusSP) ::
                    wrap(response.status.toString.getBytes(US_ASCII)) ::
                      wrap(SingleSP) ::
                        wrap(HttpResponse.defaultReason(response.status).getBytes(US_ASCII)) ::
                         wrap(CRLF) :: rawResponse.buffers
      }
    })
    def header(name: String, value: String)(rest: List[ByteBuffer]) = {
      wrap(name.getBytes(US_ASCII)) ::
        wrap(ColonSP) ::
          wrap(value.getBytes(US_ASCII)) ::
            wrap(CRLF) :: rest
    }
    @tailrec
    def headers(httpHeaders: List[HttpHeader], resConnHeader: Option[String] = None)
               (rest: List[ByteBuffer]): RawResponse = httpHeaders match {
      case HttpHeader(name, value) :: tail =>
        headers(tail, if (resConnHeader.isEmpty) if (name == "Connection") Some(value) else None else resConnHeader) {
          header(name, value)(rest)
        }
      case Nil => if (resConnHeader.isDefined) {
        RawResponse(rest, resConnHeader.get.contains("close"))
      } else reqProtocol match {
        case `HTTP/1.0` =>
          if (reqConnectionHeader.isEmpty || reqConnectionHeader.get != "Keep-Alive") {
            RawResponse(rest, closeConnection = true)
          } else RawResponse(header("Connection", "Keep-Alive")(rest), closeConnection = false)
        case `HTTP/1.1` =>
          if (reqConnectionHeader.isDefined && reqConnectionHeader.get == "close") {
            RawResponse(header("Connection", "close")(rest), closeConnection = true)
          } else RawResponse(rest, closeConnection = false)
      }
    }

    def contentLengthHeader(rest: List[ByteBuffer]) =
      if (response.body.length > 0) header("Content-Length", response.body.length.toString)(rest) else rest

    statusLine {
      headers(response.headers) {
        contentLengthHeader {
          header("Date", dateTimeNow.toRfc1123DateTimeString) {
            wrap(CRLF) ::
              wrap(response.body) :: Nil
          }
        }
      }
    }
  }

  protected def dateTimeNow = DateTime.now

}