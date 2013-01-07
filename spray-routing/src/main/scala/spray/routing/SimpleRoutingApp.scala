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

package spray.routing

import scala.concurrent.Future
import scala.concurrent.duration._
import akka.actor.{ActorRefFactory, Actor, Props}
import akka.pattern.ask
import akka.util.Timeout
import spray.can.server.{HttpServer, ServerSettings, SprayCanHttpServerApp}
import spray.io.ServerSSLEngineProvider


trait SimpleRoutingApp extends SprayCanHttpServerApp with HttpService {

  @volatile private[this] var _refFactory: Option[ActorRefFactory] = None

  implicit def actorRefFactory = _refFactory.getOrElse(
    sys.error("Route creation is not fully supported before `startServer` has been called, " +
      "maybe you can turn your route definition into a `def` ?")
  )

  /**
   * Starts a new spray-can HttpServer with the handler being a new HttpServiceActor for the given route and
   * binds the server to the given interface and port.
   * The method returns a Future on the Bound event returned by the HttpServer as a reply to the Bind command.
   * You can use the Future to determine when the server is actually up (or you can simply drop it, if you are not
   * interested in it).
   */
  def startServer(interface: String,
                  port: Int,
                  settings: ServerSettings = ServerSettings(),
                  serverActorName: String = "http-server",
                  serviceActorName: String = "simple-service-actor")
                 (route: => Route)
                 (implicit sslEngineProvider: ServerSSLEngineProvider,
                  bindingTimeout: Timeout = 1 second span): Future[HttpServer.Bound] = {
    val service = system.actorOf(
      props = Props {
        new Actor {
          _refFactory = Some(context)
          def receive = runRoute(route)
        }
      },
      name = serviceActorName
    )
    (newHttpServer(service, settings, serverActorName) ? Bind(interface, port)).mapTo[HttpServer.Bound]
  }
}