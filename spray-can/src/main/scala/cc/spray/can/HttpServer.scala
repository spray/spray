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

package cc.spray.can

import config.HttpServerConfig
import nio._
import akka.actor.ActorRef
import cc.spray.io.{ConnectionActors, NioServerActor, NioWorker, Pipelines}

class HttpServer(config: HttpServerConfig, requestActorFactory: => ActorRef)
                (nioWorker: NioWorker = new NioWorker(config))
                extends NioServerActor(config, nioWorker) with ConnectionActors {

  protected def buildConnectionPipelines(baseContext: Pipelines) = {
    StandardHttpServerFrontend(requestActorFactory) {
      HttpRequestParsing(config) {
        HttpResponseRendering(config.serverHeader) {
          ConnectionTimeoutSupport(config) {
            baseContext
          }
        }
      }
    }
  }

}