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

import org.specs2._
import specification.Step
import akka.actor.{PoisonPill, Actor}
import java.nio.ByteBuffer
import annotation.tailrec
import java.lang.IllegalStateException
import java.nio.channels.SocketChannel
import java.net.InetSocketAddress

class TestService extends Actor {
  self.id = "test-1"
  protected def receive = {
    case RequestContext(HttpRequest(method, uri, _, _, _, _), complete) => complete {
      HttpResponse(200, Nil, (method + "|" + uri).getBytes("ISO-8859-1"))
    }
  }
}

class HttpServerSpec extends Specification { def is =

  "This spec starts a new HttpServer and exercises its behavior with test requests" ^
                                                                                    Step(startServer())^
                                                                                    p^
  "test responses to simple requests"                                               ! simpleRequests^
                                                                                    Step(stopServer())


  def simpleRequests = {
    responseFor(16242) {
      """|GET /abc HTTP/1.1
         |
         |"""
    } mustEqual (200, List(HttpHeader("Content-Length", "8")), "GET|/abc")
  }

  def responseFor(port: Int)(request: String) = {
    val req = request.stripMargin.replace("\n", "\r\n")
    val channel = SocketChannel.open(new InetSocketAddress("localhost", port))
    val readBuffer = ByteBuffer.allocate(1) // we read one byte at a time

    @tailrec
    def read(parser: IntermediateParser): MessageParser = {
      readBuffer.clear()
      channel.read(readBuffer)
      readBuffer.flip()
      parser.read(readBuffer) match {
        case x: IntermediateParser => read(x)
        case x => x
      }
    }

    channel.write(ByteBuffer.wrap(req.getBytes("US-ASCII")))
    read(new EmptyResponseParser) match {
      case CompleteMessageParser(StatusLine(_, status, _), headers, _, body) =>
        channel.close()
        (status, headers.filter(_.name != "Date"), new String(body, "ISO-8859-1"))
      case ErrorMessageParser(message, _) => throw new RuntimeException("Illegal response: " + message)
      case _ => throw new IllegalStateException
    }
  }

  def startServer() {
    Actor.actorOf(new TestService).start()
    Actor.actorOf(new HttpServer(SimpleConfig(port = 16242, serviceActorId = "test-1", requestTimeout = 0))).start()
  }

  def stopServer() {
    actor("spray-can-server") ! PoisonPill
    Actor.registry.shutdownAll()
  }
}
