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
import matcher.Matcher
import specification.Step
import akka.actor.{PoisonPill, Actor}
import java.net.Socket
import org.parboiled.common.FileUtils
import java.io.{ByteArrayOutputStream, ByteArrayInputStream}

class TestService extends Actor {
  self.id = "test-1"
  protected def receive = {
    case RequestContext(HttpRequest(method, uri, _, _, _, _), complete) => complete {
      HttpResponse(200, List(HttpHeader("Content-Type", "text/plain")),(method + "|" + uri).getBytes("ISO-8859-1"))
    }
  }
}

class HttpServerSpec extends Specification { def is =

  "This spec starts a new HttpServer and exercises its behavior with test requests" ^
                                                                                    Step(startServer())^
                                                                                    p^
  "responses to simple requests" ! simpleRequests


  def simpleRequests = {
    responseFor(16242) {
      """|GET /abc HTTP/1.1
         |"""
    } must matchResponse(status = 200, body = "GET|/abc")
  }

  def matchResponse(status: Int = 0, headers: List[HttpHeader] = null, body: String = null): Matcher[HttpResponse] = {
    def f[A](g: HttpResponse => A) = g
    val statusMatcher: Matcher[HttpResponse] = f(hr => status == 0 || hr.status == status) -> f(_.status + " != " + status)
    val headerMatcher: Matcher[HttpResponse] = f(hr => headers == null || hr.headers == headers) -> f(_.headers + " != " + headers)
    val bodyMatcher: Matcher[HttpResponse] = f(hr => body == null || hr.bodyAsString() == body) -> f(_.bodyAsString() + " != " + body)
    statusMatcher and headerMatcher and bodyMatcher
  }

  def responseFor(port: Int)(request: String) = {
    /*val socket = new Socket("localhost", port)
    FileUtils.copyAll(
      new ByteArrayInputStream(request.getBytes("US-ASCII")),
      new ByteArrayOutputStream()
    )*/
    HttpResponse.of(body = "GET|/abc")
  }

  def startServer() {
    Actor.actorOf(new TestService).start()
    Actor.actorOf(new HttpServer(SimpleConfig(port = 16242, serviceActorId = "test-1", requestTimeout = 0))).start()
  }

  def stopServer() {
    actor("test-1") ! PoisonPill
    Actor.registry.shutdownAll()
  }
}
