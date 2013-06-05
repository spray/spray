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

import akka.actor.{ Props, ActorRef, ActorSystem }
import spray.io.{ ServerSSLEngineProvider, SingletonHandler, IOExtension }
import spray.util.actorSystemNameFrom

trait SprayCanHttpServerApp {

  // override if you require a special ActorSystem configuration
  lazy val system = ActorSystem(actorSystemNameFrom(getClass))

  // bring HttpServer.Bind into scope without import
  def Bind = HttpServer.Bind

  /**
   * Creates a new spray-can HttpServer actor using the given singleton handler, settings and name.
   */
  def newHttpServer(handler: ActorRef,
                    ioBridge: ActorRef = IOExtension(system).ioBridge(),
                    settings: ServerSettings = ServerSettings(),
                    name: String = "http-server")(implicit sslEngineProvider: ServerSSLEngineProvider) =
    system.actorOf(Props(new HttpServer(ioBridge, SingletonHandler(handler), settings)), name)

}