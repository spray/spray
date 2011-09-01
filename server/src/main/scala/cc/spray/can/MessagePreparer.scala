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
import ByteBuffer._

trait MessagePreparer {
  protected val US_ASCII = Charset.forName("US-ASCII")
  protected val ColonSP = ": ".getBytes(US_ASCII)
  protected val CRLF = "\r\n".getBytes(US_ASCII)
  protected val SingleSP = " ".getBytes(US_ASCII)

  protected def header(name: String, value: String)(rest: List[ByteBuffer]) = {
    wrap(name.getBytes(US_ASCII)) ::
      wrap(ColonSP) ::
        wrap(value.getBytes(US_ASCII)) ::
          wrap(CRLF) :: rest
  }

  protected def contentLengthHeader(contentLength: Int)(rest: List[ByteBuffer]) =
    if (contentLength > 0) header("Content-Length", contentLength.toString)(rest) else rest

  @tailrec
  protected final def theHeaders(httpHeaders: List[HttpHeader], connectionHeaderValue: Option[String] = None)
                                (rest: List[ByteBuffer]): (List[ByteBuffer], Option[String]) = {
    httpHeaders match {
      case HttpHeader(name, value) :: tail =>
        val newConnectionHeaderValue = {
          if (connectionHeaderValue.isEmpty)
            if (name == "Connection") Some(value) else None
          else connectionHeaderValue
        }
        theHeaders(tail, newConnectionHeaderValue)(header(name, value)(rest))
      case Nil => (rest, connectionHeaderValue)
    }
  }
}

trait ResponsePreparer extends MessagePreparer {
  private val StatusLine200 = "HTTP/1.1 200 OK\r\n".getBytes(US_ASCII)
  private val HttpVersionPlusSP = "HTTP/1.1 ".getBytes(US_ASCII)

  protected def prepare(response: HttpResponse, reqProtocol: HttpProtocol,
                        reqConnectionHeader: Option[String]): WriteJob = {
    import ByteBuffer._
    import response._

    def statusLine(writeJob: WriteJob) = writeJob.copy(buffers = {
      status match {
        case 200 => wrap(StatusLine200) :: writeJob.buffers
        case x => wrap(HttpVersionPlusSP) ::
                    wrap(status.toString.getBytes(US_ASCII)) ::
                      wrap(SingleSP) ::
                        wrap(HttpResponse.defaultReason(status).getBytes(US_ASCII)) ::
                         wrap(CRLF) :: writeJob.buffers
      }
    })

    def fixConnectionHeader(tuple: (List[ByteBuffer], Option[String])): WriteJob = {
      val (rest, connectionHeaderValue) = tuple
      if (connectionHeaderValue.isDefined) {
        WriteJob(rest, connectionHeaderValue.get.contains("close"))
      } else reqProtocol match {
        case `HTTP/1.0` =>
          if (reqConnectionHeader.isEmpty || reqConnectionHeader.get != "Keep-Alive") {
            WriteJob(rest, closeConnection = true)
          } else WriteJob(header("Connection", "Keep-Alive")(rest), closeConnection = false)
        case `HTTP/1.1` =>
          if (reqConnectionHeader.isDefined && reqConnectionHeader.get == "close") {
            WriteJob(header("Connection", "close")(rest), closeConnection = true)
          } else WriteJob(rest, closeConnection = false)
      }
    }

    statusLine {
      fixConnectionHeader {
        theHeaders(headers) {
          contentLengthHeader(body.length) {
            header("Date", dateTimeNow.toRfc1123DateTimeString) {
              wrap(CRLF) :: wrap(body) :: Nil
            }
          }
        }
      }
    }
  }

  protected def dateTimeNow = DateTime.now  // split out so we can stabilize by overriding in tests
}

trait RequestPreparer extends MessagePreparer {
  private val SPplusHttpVersionPlusCRLF = " HTTP/1.1\r\n".getBytes(US_ASCII)

  protected def prepare(request: HttpRequest): List[ByteBuffer] = {
    import request._

    def requestLine(tuple: (List[ByteBuffer], Option[String])) = {
      wrap(method.asByteArray) ::
        wrap(uri.getBytes(US_ASCII)) ::
          wrap(SPplusHttpVersionPlusCRLF) :: tuple._1
    }

    requestLine {
      theHeaders(headers) {
        contentLengthHeader(body.length) {
          wrap(CRLF) :: wrap(body) :: Nil
        }
      }
    }
  }
}