/*
 * Copyright (C) 2011-2012 spray.io
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

import org.specs2.mutable.Specification
import com.typesafe.config.{ConfigFactory, Config}
import akka.io.Tcp
import spray.testkit.Specs2PipelineStageTest
import spray.can.Http
import spray.can.rendering.HttpResponsePartRenderingContext
import spray.http.{HttpMethods, HttpResponse}
import spray.can.TestSupport._

class ResponseRenderingSpec extends Specification with Specs2PipelineStageTest {
  val stage = ResponseRendering(ServerSettings(system))

  override lazy val config: Config = ConfigFactory.parseString("""
    spray.can.server {
      server-header = spray/1.0
      response-size-hint = 256
    }""")

  "The ResponseRendering PipelineStage" should {

    "be transparent to unrelated commands" in new Fixture(stage) {
      val cmd = new Http.Command {}
      connectionActor ! cmd
      commands.expectMsg(cmd)
    }

    "translate a simple HttpResponsePartRenderingContext into the corresponding Tcp.Write command" in new Fixture(stage) {
      connectionActor ! HttpResponsePartRenderingContext(HttpResponse(entity = "Some Message"))
      wipeDate(commands.expectMsgType[Tcp.Write].data.utf8String) === prep {
        """HTTP/1.1 200 OK
          |Server: spray/1.0
          |Date: XXXX
          |Content-Type: text/plain
          |Content-Length: 12
          |
          |Some Message"""
      }
    }

    "append a Close command to the Tcp.Write if the connection is to be closed" in new Fixture(stage) {
      connectionActor ! HttpResponsePartRenderingContext(
        responsePart = HttpResponse(entity = "Some Message"),
        requestMethod = HttpMethods.HEAD,
        requestConnectionHeader = Some("close")
      )
      wipeDate(commands.expectMsgType[Tcp.Write].data.utf8String) === prep {
        """HTTP/1.1 200 OK
          |Server: spray/1.0
          |Date: XXXX
          |Content-Type: text/plain
          |Connection: close
          |Content-Length: 12
          |
          |"""
      }
      commands.expectMsg(Http.Close)
    }
  }
}
