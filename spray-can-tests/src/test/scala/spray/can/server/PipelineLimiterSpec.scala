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

package spray.can.server

import akka.util.duration._
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import akka.io.Tcp
import spray.testkit.Specs2PipelineStageTest
import spray.can.Http
import spray.can.rendering.ResponsePartRenderingContext
import spray.http._

class PipelineLimiterSpec extends Specification with Specs2PipelineStageTest with NoTimeConversions {
  val stage = PipeliningLimiter(2)

  "The PipeliningLimiter PipelineStage" should {

    "produce Tcp.SuspendReading and Tcp.ResumeReading commands as required" in new Fixture(stage) {
      connectionActor ! request("a")
      events.expectMsg(request("a"))

      connectionActor ! request("b")
      events.expectMsg(request("b"))

      connectionActor ! request("c")
      commands.expectMsg(Tcp.SuspendReading)
      events.expectNoMsg(50.millis)

      connectionActor ! request("d")
      commands.expectMsg(Tcp.SuspendReading)
      events.expectNoMsg(50.millis)

      connectionActor ! request("e")
      commands.expectMsg(Tcp.SuspendReading)
      events.expectNoMsg(50.millis)

      connectionActor ! response("a")
      commands.expectMsg(response("a"))
      events.expectMsg(request("c"))

      connectionActor ! response("b")
      commands.expectMsg(response("b"))
      events.expectMsg(request("d"))

      connectionActor ! response("c")
      commands.expectMsg(response("c"))
      commands.expectMsg(Tcp.ResumeReading)
      events.expectMsg(request("e"))

      connectionActor ! response("d")
      commands.expectMsg(response("d"))
      commands.expectNoMsg(50.millis)
      events.expectNoMsg(50.millis)

      connectionActor ! response("e")
      commands.expectMsg(response("e"))
      commands.expectNoMsg(50.millis)
    }

    "properly handle chunked requests" in new Fixture(stage) {
      connectionActor ! request("a")
      events.expectMsg(request("a"))

      connectionActor ! requestStart("b")
      events.expectMsg(requestStart("b"))
      connectionActor ! requestChunk("b1")
      events.expectMsg(requestChunk("b1"))
      connectionActor ! requestChunk("b2")
      events.expectMsg(requestChunk("b2"))
      connectionActor ! requestEnd("b")
      events.expectMsg(requestEnd("b"))

      connectionActor ! response("a")
      commands.expectMsg(response("a"))

      connectionActor ! requestStart("c")
      events.expectMsg(requestStart("c"))
      connectionActor ! requestChunk("c1")
      events.expectMsg(requestChunk("c1"))
      connectionActor ! requestChunk("c2")
      events.expectMsg(requestChunk("c2"))
      connectionActor ! requestEnd("c")
      events.expectMsg(requestEnd("c"))

      connectionActor ! requestStart("d")
      commands.expectMsg(Tcp.SuspendReading)
      events.expectNoMsg(50.millis)
      connectionActor ! requestChunk("d1")
      commands.expectMsg(Tcp.SuspendReading)
      events.expectNoMsg(50.millis)
      connectionActor ! requestChunk("d2")
      commands.expectMsg(Tcp.SuspendReading)
      events.expectNoMsg(50.millis)

      connectionActor ! response("b")
      commands.expectMsg(response("b"))
      commands.expectMsg(Tcp.ResumeReading)
      events.expectMsg(requestStart("d"))
      events.expectMsg(requestChunk("d1"))
      events.expectMsg(requestChunk("d2"))

      connectionActor ! requestEnd("d")
      events.expectMsg(requestEnd("d"))

      connectionActor ! response("c")
      commands.expectMsg(response("c"))

      connectionActor ! response("d")
      commands.expectMsg(response("d"))
    }
  }

  def request(body: String) = Http.MessageEvent(HttpRequest(entity = body))
  def requestStart(body: String) = Http.MessageEvent(ChunkedRequestStart(HttpRequest(entity = body)))
  def requestChunk(body: String) = Http.MessageEvent(MessageChunk(body))
  def requestEnd(ext: String) = Http.MessageEvent(ChunkedMessageEnd(ext))

  def response(body: String) = ResponsePartRenderingContext(HttpResponse(entity = body))
  def responseStart(body: String) = ResponsePartRenderingContext(HttpResponse(entity = body))
  def responseChunk(body: String) = ResponsePartRenderingContext(MessageChunk(body))
  def responseEnd(ext: String) = ResponsePartRenderingContext(ChunkedMessageEnd(ext))
}
