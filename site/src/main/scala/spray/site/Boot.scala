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

package spray.site

import akka.actor.{Props, ActorSystem}
import spray.can.server.HttpServer
import spray.io._


object Boot extends App {
  // we need an ActorSystem to host our application in
  val system = ActorSystem("demo")

  // every spray-can HttpServer (and HttpClient) needs an IOBridge for low-level network IO
  // (but several servers and/or clients can share one)
  val ioBridge = new IOBridge(system).start()

  // create and start our service actor
  val service = system.actorOf(Props[SiteServiceActor], "site-service")

  // create and start the spray-can HttpServer, telling it that
  // we want requests to be handled by our singleton service actor
  val httpServer = system.actorOf(
    Props(new HttpServer(ioBridge, SingletonHandler(service))),
    name = "http-server"
  )

  // a running HttpServer can be bound, unbound and rebound
  // initially to need to tell it where to bind to
  val settings = SiteSettings.Default
  httpServer ! HttpServer.Bind(settings.Interface, settings.Port)

  // finally we drop the main thread but hook the shutdown of
  // our IOBridge into the shutdown of the applications ActorSystem
  system.registerOnTermination {
    ioBridge.stop()
  }
}