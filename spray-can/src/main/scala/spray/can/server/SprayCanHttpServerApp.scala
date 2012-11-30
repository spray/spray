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

import akka.actor.{Props, ActorRef, ActorSystem}
import spray.io.{ServerSSLEngineProvider, SingletonHandler, IOExtension}
import spray.util.actorSystemNameFrom


trait SprayCanHttpServerApp {

  // override if you require a special ActorSystem configuration
  val system = ActorSystem(actorSystemNameFrom(getClass))

  // every spray-can HttpServer (and HttpClient) needs an IOBridge for low-level network IO
  // (but several servers and/or clients can share one)
  val ioBridge = IOExtension(system).ioBridge

  val Bind = HttpServer.Bind

  def newHttpServer(handler: ActorRef, settings: ServerSettings = ServerSettings(), name: String = "http-server")
                   (implicit sslEngineProvider: ServerSSLEngineProvider) =
    system.actorOf(Props(new HttpServer(ioBridge, SingletonHandler(handler), settings)), name)

}