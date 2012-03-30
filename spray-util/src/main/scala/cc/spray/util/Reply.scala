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

import akka.spray.LazyActorRef
import akka.actor.{ActorPath, ActorRef}

case class Reply(reply: Any, context: Any)

object Reply {

  /**
   * Creates an ActorRef to be used as the sender of a message, which will forward all replies back to the
   * actor this method was called in. All replies will be wrapped in a `Reply` instance holding the actual reply
   * message as well as the context that was given as a parameter to this method.
   * The overhead introduced by this mechanism of context keeping is really small, which makes it a viable solution
   * for **local-only** messaging protocols.
   *
   * CAUTION: The ActorRef created by this method is **not** registered with any provider, meaning that it cannot
   * be found via its path. It also will **not** be reachable from non-local JVMs!
   */
  def withContext(context: Any)(implicit replyReceiver: ActorRef): LazyActorRef = new LazyActorRef(replyReceiver) {
    def handle(reply: Any, replySender: ActorRef) {
      replyReceiver.tell(new Reply(reply, context), replySender)
    }
    override protected def register(path: ActorPath) {}
    override protected def unregister(path: ActorPath) {}
  }
}
