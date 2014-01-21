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

package spray.can.client

import akka.actor.{ SupervisorStrategy, ActorRef, Props, Actor }
import spray.can.{ Http, HttpExt }

private[can] class HttpClientSettingsGroup(settings: ClientConnectionSettings,
                                           httpSettings: HttpExt#Settings) extends Actor {
  val connectionCounter = Iterator from 0
  val pipelineStage = HttpClientConnection.pipelineStage(settings)

  def receive = {
    case connect: Http.Connect ⇒
      val commander = sender()
      context.actorOf(
        props = Props(new HttpClientConnection(commander, connect, pipelineStage, settings))
          .withDispatcher(httpSettings.ConnectionDispatcher),
        name = connectionCounter.next().toString)

    case Http.CloseAll(cmd) ⇒
      val children = context.children.toSet
      if (children.isEmpty) {
        sender() ! Http.ClosedAll
        context.stop(self)
      } else {
        children foreach { _ ! cmd }
        context.become(closing(children, Set(sender())))
      }
  }

  def closing(children: Set[ActorRef], commanders: Set[ActorRef]): Receive = {
    case _: Http.CloseAll ⇒
      context.become(closing(children, commanders + sender()))

    case _: Http.ConnectionClosed ⇒
      val stillRunning = children - sender()
      if (stillRunning.isEmpty) {
        commanders foreach (_ ! Http.ClosedAll)
        context.stop(self)
      } else context.become(closing(stillRunning, commanders))
  }
}
