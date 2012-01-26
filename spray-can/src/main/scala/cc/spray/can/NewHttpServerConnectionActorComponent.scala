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

import _root_..
import _root_..
import _root_..{NewLinkedList, Received, Handle, Key}
import akka.actor.Actor
import java.nio.ByteBuffer
import annotation.tailrec
import java.nio.channels.SocketChannel

trait NewHttpServerConnectionActorComponent {

  def startRequestParser: EmptyRequestParser

  class ConnectionActor(val key: Key) extends Actor with Handle {
    val requestRecords = new RequestRecordList
    var messageParser: MessageParser = startRequestParser
    var requestsDispatched = 0
    var responseNr = 0

    def handler = self

    protected def receive = {
      case x: Received => processReceivedBytes(x.buffer)
    }

    @tailrec
    def processReceivedBytes(buffer: ByteBuffer) {
      messageParser match {
        case x: IntermediateParser =>
          val recurse = x.read(buffer) match {
            case x: IntermediateParser => messageParser = x; false
            case x: CompleteMessageParser => handleCompleteMessage(x); true
            case x: ChunkedStartParser => handleChunkedStart(x); true
            case x: ChunkedChunkParser => handleChunkedChunk(x); true
            case x: ChunkedEndParser => handleChunkedEnd(x); true
            case x: ErrorParser => handleParseError(x); false
          }
          if (recurse && buffer.remaining > 0) processReceivedBytes(buffer)
        case x: ErrorParser => handleParseError(x)
      }
    }

    def handleCompleteMessage(parser: CompleteMessageParser) {
      val requestLine = parser.messageLine.asInstanceOf[RequestLine]
      import requestLine._
      log.debug("Dispatching {} request to '{}' to the service actor", method, uri)
      val remoteAddress = key.channel.asInstanceOf[SocketChannel].socket.getInetAddress
      val request = HttpRequest(method, uri, parser.headers, parser.body, protocol)
      var requestRecord: Option[requestRecords.Record] = None
      val responder = new DefaultRequestResponder(requestLine, countDispatch(), parser.connectionHeader, requestRecord)
      val reqContext = RequestContext(request, remoteAddress, responder)
      if (requestTimeoutCycle.isDefined) requestRecord = Some(new requestRecords.Record(reqContext))
      serviceActor ! reqContext
      messageParser = startRequestParser // switch back to parsing the next request from the start
    }

    def countDispatch() = {
      val nextResponseNr = requestsDispatched
      requestsDispatched += 1
      nextResponseNr
    }
  }
}

class RequestRecordList extends NewLinkedList { list =>
  type Elem = Record

  class Record(val context: RequestContext) extends Element {
    list += this
  }
}