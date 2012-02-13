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

package cc.spray.io

import akka.actor.Actor

trait ConnectionActors extends NioPeer {

  protected def createConnectionHandle(key: Key): Handle = {
    lazy val actor = new NioConnectionActor(key)
    Actor.actorOf(actor).start()
    actor
  }

  protected def buildConnectionPipelines(baseContext: Pipelines): Pipelines

  class NioConnectionActor(val key: Key) extends Actor with Handle {

    private val context = buildConnectionPipelines {
      Pipelines(
        handle = this,
        upstream = {
          case x: Closed =>
            log.debug("Stopping connection actor, connection was closed due to {}", x.reason)
            self.stop()
          case x: CommandError => log.warn("Received {}", x)
          case x => log.warn("upstreamPipeline: dropped {}", x)
        },
        downstream = {
          case x: Send => nioWorker ! x
          case x: Close => nioWorker ! x
          case x => log.warn("downstreamPipeline: dropped {}", x)
        }
      )
    }

    protected def receive = {
      case msg if self.channel == nioWorker => context.upstream(msg)
      case msg => context.downstream(msg)
    }

    def handler = self
  }

}