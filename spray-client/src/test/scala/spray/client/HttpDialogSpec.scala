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

package spray.client

import com.typesafe.config.ConfigFactory
import akka.util.duration._
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import akka.actor.{ Actor, Props, ActorSystem }
import akka.pattern.ask
import akka.testkit.TestProbe
import akka.io.IO
import spray.can.Http
import spray.util._
import spray.http._

class HttpDialogSpec extends Specification with NoTimeConversions {
  val testConf = ConfigFactory.parseString("""
    akka {
      event-handlers = ["akka.testkit.TestEventListener"]
      loglevel = WARNING
    }""")
  implicit val system = ActorSystem(Utils.actorSystemNameFrom(getClass), testConf)
  import system.dispatcher
  val (interface, port) = Utils.temporaryServerHostnameAndPort()
  val connect = Http.Connect(interface, port)

  step {
    val testService = system.actorOf {
      Props {
        new Actor {
          def receive = {
            case x: Http.Connected        ⇒ sender ! Http.Register(self)
            case x: HttpRequest           ⇒ sender ! HttpResponse(entity = x.uri.path.toString)
            case _: Http.ConnectionClosed ⇒ // ignore
          }
        }
      }
    }
    IO(Http).ask(Http.Bind(testService, interface, port))(3.seconds).await
  }

  "An HttpDialog" should {
    "be able to complete a simple request/response dialog" in {
      HttpDialog(connect)
        .send(HttpRequest(uri = "/foo"))
        .end
        .map(_.entity.asString)
        .await === "/foo"
    }
    "be able to complete a pipelined 3 requests dialog" in {
      HttpDialog(connect)
        .send(HttpRequest(uri = "/foo"))
        .send(HttpRequest(uri = "/bar"))
        .send(HttpRequest(uri = "/baz"))
        .end
        .map(_.map(_.entity.asString))
        .await === "/foo" :: "/bar" :: "/baz" :: Nil
    }
    "be able to complete an unpipelined 3 requests dialog" in {
      HttpDialog(connect)
        .send(HttpRequest(uri = "/foo"))
        .awaitResponse
        .send(HttpRequest(uri = "/bar"))
        .awaitAllResponses
        .send(HttpRequest(uri = "/baz"))
        .end
        .map(_.map(_.entity.asString))
        .await === "/foo" :: "/bar" :: "/baz" :: Nil
    }
    "be able to complete a dialog with 3 replies" in {
      HttpDialog(connect)
        .send(HttpRequest(uri = "/foo"))
        .reply(response ⇒ HttpRequest(uri = response.entity.asString + "/a"))
        .reply(response ⇒ HttpRequest(uri = response.entity.asString + "/b"))
        .reply(response ⇒ HttpRequest(uri = response.entity.asString + "/c"))
        .end
        .map(_.entity.asString)
        .await === "/foo/a/b/c"
    }
    "properly deliver error messages from the server" in {
      HttpDialog(connect)
        .send(HttpRequest(uri = "/abc/" + ("x" * 2048)))
        .end
        .await.withHeaders(Nil) ===
        HttpResponse(StatusCodes.RequestUriTooLong, "URI length exceeds the configured limit of 2048 characters")
    }
  }

  step {
    //    val probe = TestProbe()
    //    probe.send(IO(Http), Http.CloseAll)
    //    probe.expectMsg(5.seconds, Http.ClosedAll)
    system.shutdown()
  }
}
