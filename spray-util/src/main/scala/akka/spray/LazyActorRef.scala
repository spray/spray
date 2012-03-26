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

package akka.spray

import annotation.tailrec
import java.util.concurrent.atomic.AtomicReference
import akka.dispatch.{Terminate, SystemMessage}
import akka.actor._

abstract class LazyActorRef(val provider: ActorRefProvider) extends akka.actor.MinimalActorRef {
  def this(related: ActorRef) = this(related.asInstanceOf[InternalActorRef].provider)

  private[this] val state = new AtomicReference[AnyRef]

  import LazyActorRef._

  override def getParent = provider.tempContainer

  /**
   * Contract of this method:
   * Must always return the same ActorPath, which must have
   * been registered if we haven't been stopped yet.
   */
  @tailrec
  final def path: ActorPath = state.get match {
    case null ⇒
      if (state.compareAndSet(null, Registering)) {
        var p: ActorPath = null
        try {
          p = provider.tempPath()
          provider.registerTempActor(this, p)
          p
        } finally { state.set(p) }
      } else path
    case p: ActorPath       ⇒ p
    case StoppedWithPath(p) ⇒ p
    case Stopped ⇒
      // even if we are already stopped we still need to produce a proper path
      state.compareAndSet(Stopped, StoppedWithPath(provider.tempPath()))
      path
    case Registering ⇒ path // spin until registration is completed
  }

  override def !(message: Any)(implicit sender: ActorRef = null) {
    state.get match {
      case Stopped | _: StoppedWithPath ⇒ provider.deadLetters ! message
      case _ ⇒ deliver(message, sender)
    }
  }

  protected def deliver(message: Any, sender: ActorRef)

  override def sendSystemMessage(message: SystemMessage) {
    message match {
      case _: Terminate ⇒ stop()
      case _            ⇒
    }
  }

  override def isTerminated = state.get match {
    case Stopped | _: StoppedWithPath ⇒ true
    case _                            ⇒ false
  }

  @tailrec
  final override def stop() {
    state.get match {
      case null ⇒
        // if path was never queried nobody can possibly be watching us, so we don't have to publish termination either
        if (state.compareAndSet(null, Stopped)) doStop()
        else stop()
      case p: ActorPath ⇒
        if (state.compareAndSet(p, StoppedWithPath(p))) {
          try {
            doStop()
            provider.deathWatch.publish(Terminated(this))
          } finally {
            provider.unregisterTempActor(p)
          }
        } else stop()
      case Stopped | _: StoppedWithPath ⇒
      case Registering                  ⇒ stop() // spin until registration is completed before stopping
    }
  }

  // overridable callback
  def doStop() {}
}

object LazyActorRef {
  private case object Registering
  private case object Stopped
  private case class StoppedWithPath(path: ActorPath)
}