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

import com.typesafe.config.ConfigFactory
import org.specs2.mutable.Specification
import akka.actor.ActorSystem
import spray.can.rendering.HttpResponsePartRenderingContext
import spray.io.{IOPeer, Command}
import spray.can.HttpPipelineStageSpec
import spray.util.CleanClose
import spray.http._


class ResponseRenderingSpec extends Specification with HttpPipelineStageSpec {
  val system = ActorSystem()

  "The ResponseRendering PipelineStage" should {
    "be transparent to unrelated commands" in {
      val cmd = new Command {}
      pipelineStage.test {
        val Commands(command) = process(cmd)
        command === cmd
      }
    }
    "translate a simple HttpResponsePartRenderingContext into the corresponding Send command" in {
      pipelineStage.test {
        val Commands(command) = process(HttpResponsePartRenderingContext(HttpResponse(entity = "Some Message")))
        command === sendString {
          """|HTTP/1.1 200 OK
            |Server: spray/1.0
            |Date: XXXX
            |Content-Type: text/plain
            |Content-Length: 12
            |
            |Some Message"""
        }
      }
    }
    "append a Close command to the Send if the connection is to be closed" in {
      pipelineStage.test {
        val Commands(commands@ _*) = process(
          HttpResponsePartRenderingContext(
            responsePart = HttpResponse(entity = "Some Message"),
            requestMethod = HttpMethods.HEAD,
            requestConnectionHeader = Some("close")
          )
        )
        commands(0) === sendString {
          """|HTTP/1.1 200 OK
             |Connection: close
             |Server: spray/1.0
             |Date: XXXX
             |Content-Type: text/plain
             |Content-Length: 12
             |
             |"""
        }
        commands(1) === IOPeer.Close(CleanClose)
      }
    }
  }

  step(system.shutdown())

  /////////////////////////// SUPPORT ////////////////////////////////

  val pipelineStage =
    ResponseRendering(
      new ServerSettings(
        ConfigFactory.parseString("""
          spray.can.server.server-header = spray/1.0
          spray.can.server.response-size-hint = 256
        """)
      )
    )

  def sendString(rawMessage: String) = SendString(prep(rawMessage))
}
