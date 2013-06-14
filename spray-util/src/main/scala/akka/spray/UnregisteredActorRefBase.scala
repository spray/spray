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

package akka.spray

import scala.annotation.tailrec
import akka.util.Unsafe
import akka.dispatch._
import akka.actor._

abstract class UnregisteredActorRefBase(val provider: ActorRefProvider) extends MinimalActorRef {
  import UnregisteredActorRefBase._

  @volatile
  private[this] var _stateDoNotCallMeDirectly: AnyRef = _

  @inline
  private[this] def state: AnyRef = Unsafe.instance.getObjectVolatile(this, stateOffset)

  @inline
  private[this] def updateState(oldState: AnyRef, newState: AnyRef): Boolean =
    Unsafe.instance.compareAndSwapObject(this, stateOffset, oldState, newState)

  @inline
  private[this] def setState(newState: AnyRef): Unit = {
    Unsafe.instance.putObjectVolatile(this, stateOffset, newState)
  }

  override def getParent: InternalActorRef = provider.tempContainer

  /**
   * Contract of this method:
   * Must always return the same ActorPath, which must have
   * been registered if we haven't been stopped yet.
   */
  @tailrec
  final def path: ActorPath = state match {
    case null ⇒
      if (updateState(null, Registering)) {
        var p: ActorPath = null
        try {
          p = provider.tempPath()
          register(p)
          p
        } finally { setState(p) }
      } else path
    case p: ActorPath       ⇒ p
    case StoppedWithPath(p) ⇒ p
    case Stopped ⇒
      // even if we are already stopped we still need to produce a proper path
      updateState(Stopped, StoppedWithPath(provider.tempPath()))
      path
    case Registering ⇒ path // spin until registration is completed
  }

  override def !(message: Any)(implicit sender: ActorRef = Actor.noSender) {
    state match {
      case Stopped | _: StoppedWithPath ⇒ provider.deadLetters ! message
      case _                            ⇒ handle(message)
    }
  }

  override def sendSystemMessage(message: SystemMessage): Unit = {
    message match {
      case _: Terminate ⇒ stop()
      case _            ⇒
    }
  }

  override def isTerminated: Boolean = state match {
    case Stopped | _: StoppedWithPath ⇒ true
    case _                            ⇒ false
  }

  @tailrec
  override final def stop(): Unit = {
    state match {
      case null ⇒ // if path was never queried nobody can possibly be watching us, so we don't have to publish termination either
        if (updateState(null, Stopped)) onStop() else stop()
      case p: ActorPath ⇒
        if (updateState(p, StoppedWithPath(p))) { try onStop() finally unregister(p) } else stop()
      case Stopped | _: StoppedWithPath ⇒ // already stopped
      case Registering                  ⇒ stop() // spin until registration is completed before stopping
    }
  }

  // callbacks

  protected def handle(message: Any)(implicit sender: ActorRef)

  protected def onStop(): Unit = {}

  protected def register(path: ActorPath): Unit = {}

  protected def unregister(path: ActorPath): Unit = {}
}

object UnregisteredActorRefBase {
  private val stateOffset = Unsafe.instance.objectFieldOffset(
    classOf[UnregisteredActorRefBase].getDeclaredField("_stateDoNotCallMeDirectly"))

  private case object Registering
  private case object Stopped
  private case class StoppedWithPath(path: ActorPath)
}