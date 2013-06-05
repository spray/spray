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

package akka.spray

import akka.actor._
import akka.util.Timeout

/**
 * An ActorRef which
 * - offers the ability to hook caller-side logic into the reply message path
 * - is never registered anywhere, i.e. can be GCed as soon the receiver drops it or is GCed itself
 *
 * CAUTION: This ActorRef is _not_ addressable from a non-local JVM and it also breaks some otherwise
 * valid invariants like `system.actorFor(ref.path.toString).equals(ref)` in the local-only context.
 * It should therefore be used only in purely local environments and in consideration of the limitations.
 * You can, however, manually wrap it with a registered ActorRef using one of the register... calls.
 */
abstract class UnregisteredActorRef(p: ActorRefProvider) extends LazyActorRef(p) { unregistered ⇒
  def this(related: ActorRef) = this(RefUtils.provider(related))
  def this(actorRefFactory: ActorRefFactory) = this(RefUtils.provider(actorRefFactory))

  override protected def register(path: ActorPath) {}

  override protected def unregister(path: ActorPath) {}

  /**
   * Produces a LazyActorRef that wraps this UnregisteredActorRef.
   * The resulting ActorRef is reachable from remote JVMs, but can only receive a single reply, which
   * has to arrive within the given timeout period.
   */
  def registerForSingleResponse(timeout: Timeout): ActorRef =
    registerForMultiResponse(UnregisteredActorRef.EveryMessageIsLastResponse, timeout)

  /**
   * Produces a LazyActorRef that wraps this UnregisteredActorRef.
   * The resulting ActorRef is reachable from remote JVMs and can receive several replies.
   * However, the last one must be identifiable and has to arrive within the given timeout period.
   */
  def registerForMultiResponse(isLastResponse: Any ⇒ Boolean, timeout: Timeout): ActorRef =
    new LazyActorRef(provider) {
      val timer = provider.scheduler.scheduleOnce(timeout.duration) {
        stop()
      }
      def handle(message: Any)(implicit sender: ActorRef) {
        unregistered.handle(message)
        if (isLastResponse(message)) {
          stop()
          timer.cancel()
        }
      }
    }

  /**
   * Produces a LazyActorRef that wraps this UnregisteredActorRef.
   * The resulting ActorRef is reachable from remote JVMs and can receive any number of replies.
   * Its lifetime is tied to the given actor, i.e. it is automatically stopped and unregistered when the given
   * actor terminates.
   */
  def registerWithDeathWatch(dieWith: ActorRef): ActorRef =
    new LazyActorRef(provider) {
      provider.deathWatch.subscribe(this, dieWith)

      def handle(message: Any)(implicit sender: ActorRef) {
        message match {
          case Terminated(`dieWith`) ⇒ stop()
          case msg                   ⇒ unregistered.handle(msg)
        }
      }
    }
}

object UnregisteredActorRef {
  val EveryMessageIsLastResponse = (_: Any) ⇒ true
}
