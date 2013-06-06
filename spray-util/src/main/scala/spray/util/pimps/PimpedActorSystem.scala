/*
 * Copyright (C) 2011-2013 spray.io
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

package spray.util.pimps

import akka.util.duration._
import akka.dispatch.Future
import akka.pattern.ask
import akka.actor._
import akka.util.Duration

class PimpedActorSystem(underlying: ActorSystem) {

  def terminationOf(subject: ActorRef): Future[Terminated] = {
    underlying.actorOf {
      Props {
        new Actor {
          var receiver: Option[ActorRef] = None

          def receive = {
            case subject: ActorRef ⇒
              context.watch(subject)
              receiver = Some(sender)
            case x: Terminated ⇒
              receiver.foreach(_ ! x)
              context.stop(self)
          }
        }
      }
    }
  }.ask(subject)(Duration.Inf).mapTo[Terminated]

}
