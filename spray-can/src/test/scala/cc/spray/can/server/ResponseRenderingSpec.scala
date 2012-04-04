/*
 * Copyright (C) 2011, 2012 Mathias Doenitz
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

package cc.spray.can.server

import cc.spray.can.model.{HttpMethods, HttpResponse}
import cc.spray.can.rendering.HttpResponsePartRenderingContext
import cc.spray.io.{CleanClose, IoPeer, Command}
import org.specs2.mutable.Specification
import cc.spray.can.HttpPipelineStageSpec

class ResponseRenderingSpec extends Specification with HttpPipelineStageSpec {

  "The ResponseRendering PipelineStage" should {
    "be transparent to unrelated commands" in {
      val command = new Command {}
      fixture(command).commands === Seq(command)
    }
    "translate a simple HttpResponsePartRenderingContext into the corresponding Send command" in {
      fixture(
        HttpResponsePartRenderingContext(HttpResponse().withBody("Some Message"))
      ).commands.fixSends === Seq(SendString(
        """|HTTP/1.1 200 OK
           |Server: spray/1.0
           |Date: XXXX
           |Content-Length: 12
           |
           |Some Message"""
      ))
    }
    "append a Close command to the Send if the connection is to be closed" in {
      fixture(
        HttpResponsePartRenderingContext(
          responsePart = HttpResponse().withBody("Some Message"),
          requestMethod = HttpMethods.HEAD,
          requestConnectionHeader = Some("close")
        )
      ).commands.fixSends === Seq(
        SendString(
          """|HTTP/1.1 200 OK
             |Connection: close
             |Server: spray/1.0
             |Date: XXXX
             |Content-Length: 12
             |
             |"""
        ),
        IoPeer.Close(CleanClose)
      )
    }
  }

  step {
    cleanup()
  }

  /////////////////////////// SUPPORT ////////////////////////////////

  val fixture = new Fixture(
    ResponseRendering(
      serverHeader = "spray/1.0",
      chunklessStreaming = false,
      responseSizeHint = 256
    )
  )

  override def SendString(rawMessage: String) = super.SendString(prep(rawMessage))
}
