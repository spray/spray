/*
 * Copyright © 2011-2013 the spray project <http://spray.io>
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
import scala.collection.immutable
import akka.actor.{ ActorSystem, ActorRefFactory, Actor, Props }
import akka.pattern.ask
import akka.util.Timeout
import akka.io.{ Inet, IO, Tcp }
import spray.io.ServerSSLEngineProvider
import spray.can.Http
import spray.can.server.ServerSettings

trait SimpleRoutingApp extends HttpService {

  @volatile private[this] var _refFactory: Option[ActorRefFactory] = None

  implicit def actorRefFactory = _refFactory getOrElse sys.error(
    "Route creation is not fully supported before `startServer` has been called, " +
      "maybe you can turn your route definition into a `def` ?")

  /**
   * Starts a new spray-can HTTP server with a default HttpService for the given route and binds the server to the
   * given interface and port.
   * The method returns a Future on the Bound event returned by the HttpListener as a reply to the Bind command.
   * You can use the Future to determine when the server is actually up (or you can simply drop it if you are not
   * interested in it).
   */
  def startServer(interface: String,
                  port: Int,
                  serviceActorName: String = "simple-service-actor",
                  backlog: Int = 100,
                  options: immutable.Traversable[Inet.SocketOption] = Nil,
                  settings: Option[ServerSettings] = None)(route: ⇒ Route)(implicit system: ActorSystem, sslEngineProvider: ServerSSLEngineProvider,
                                                                           bindingTimeout: Timeout = 1.second): Future[Http.Bound] = {
    val serviceActor = system.actorOf(
      props = Props {
        new Actor {
          _refFactory = Some(context)
          def receive = {
            val system = 0 // shadow implicit system
            runRoute(route)
          }
        }
      },
      name = serviceActorName)
    IO(Http).ask(Http.Bind(serviceActor, interface, port, backlog, options, settings)).flatMap {
      case b: Http.Bound ⇒ Future.successful(b)
      case Tcp.CommandFailed(b: Http.Bind) ⇒
        // TODO: replace by actual exception when Akka #3861 is fixed.
        //       see https://www.assembla.com/spaces/akka/tickets/3861
        Future.failed(new RuntimeException(
          "Binding failed. Switch on DEBUG-level logging for `akka.io.TcpListener` to log the cause."))
    }(system.dispatcher)
  }
}