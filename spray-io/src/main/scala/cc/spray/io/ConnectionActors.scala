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

import akka.actor.{PoisonPill, Props, Actor}


trait ConnectionActors extends IoPeerActor {

  override protected def createConnectionHandle(key: Key): Handle = {
    lazy val actor = new IoConnectionActor(key)
    context.actorOf(Props(actor))
    actor
  }

  protected def buildConnectionPipelines(basePipelines: Pipelines): Pipelines

  class IoConnectionActor(val key: Key) extends Actor with Handle {
    private val pipelines = buildConnectionPipelines {
      Pipelines(
        handle = this,
        eventPipeline = {
          case x: Closed =>
            log.debug("Stopping connection actor, connection was closed due to {}", x.reason)
            self ! PoisonPill
          case x: CommandError => log.warning("Received {}", x)
          case x => log.warning("eventPipeline: dropped {}", x)
        },
        commandPipeline = {
          case x: Send => ioWorker ! x
          case x: Close => ioWorker ! x
          case x => log.warning("commandPipeline: dropped {}", x)
        }
      )
    }

    protected def receive = {
      case x: Event => pipelines.eventPipeline(x)
      case x: Command => pipelines.commandPipeline(x)
    }

    def handler = self
  }

}