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

package cc.spray.util

import akka.actor.ActorRef
import akka.spray.LazyActorRef

case class Reply(reply: Any, context: Any)

object Reply {
  def onceWithContext(context: Any)(implicit replyReceiver: ActorRef): LazyActorRef = new LazyActorRef(replyReceiver) {
    def handle(reply: Any, replySender: ActorRef) {
      replyReceiver.tell(new Reply(reply, context), replySender)
      stop()
    }
  }
}
