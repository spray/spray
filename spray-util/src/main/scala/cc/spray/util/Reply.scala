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

package spray.util

import akka.actor.ActorRef
import akka.spray.UnregisteredActorRef

case class Reply(reply: Any, context: Any)

object Reply {

  /**
   * Creates an ActorRef to be used as the sender of a message, which will forward all replies back to the
   * actor this method was called in. All replies will be wrapped in a `Reply` instance holding the actual reply
   * message as well as the context that was given as a parameter to this method.
   * The overhead introduced by this mechanism of context keeping is really small, which makes it a viable solution
   * for **local-only** messaging protocols.
   *
   * CAUTION: This ActorRef is _not_ addressable from a non-local JVM and it also breaks some otherwise
   * valid invariants like `system.actorFor(ref.path.toString).equals(ref)` in the local-only context.
   * It should therefore be used only in purely local environments and in consideration of the limitations.
   */
  def withContext(context: Any)(implicit replyReceiver: ActorRef): UnregisteredActorRef = {
    new UnregisteredActorRef(replyReceiver) {
      def handle(reply: Any)(implicit replySender: ActorRef) {
        replyReceiver.tell(new Reply(reply, context), replySender)
      }
    }
  }
}
