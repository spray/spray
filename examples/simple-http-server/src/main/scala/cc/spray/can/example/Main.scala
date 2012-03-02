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
package example

import akka.actor._
import cc.spray.io.pipelines.MessageHandlerDispatch
import cc.spray.io.IoWorker
import cc.spray.io.util._

object Main extends App {
  val system = ActorSystem("SimpleHttpServer")
  val handler = system.actorOf(Props[TestService])
  val ioWorker = new IoWorker().start()
  val server = system.actorOf(
    props = Props(new HttpServer(ioWorker, MessageHandlerDispatch.SingletonHandler(handler))),
    name = "http-server"
  )
  server ! HttpServer.Bind("localhost", 8080)
  system.terminationOf(server).await // blocks
  ioWorker.stop().get.await()
  system.shutdown()
}