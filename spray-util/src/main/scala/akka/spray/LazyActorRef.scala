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

import annotation.tailrec
import akka.dispatch.{ Terminate, SystemMessage }
import akka.actor._
import akka.util.Unsafe

/**
 * An ActorRef which
 * - offers the ability to hook caller-side logic into a `tell`
 * - is registered lazily
 *
 * CAUTION: In order to prevent memory leaks you need to make sure that the
 * ref is explicitly stopped via `stop` in _all_ cases, even if `tell`/bang is never called!
 */
abstract class LazyActorRef(val provider: ActorRefProvider) extends akka.actor.MinimalActorRef {
  def this(related: ActorRef) = this(RefUtils.provider(related))
  def this(system: ActorSystem) = this{
    system match {
      case x: ExtendedActorSystem ⇒ x.provider
      case _                      ⇒ throw new IllegalArgumentException("Unsupported ActorSystem implementation")
    }
  }
  def this(context: ActorContext) = this(context.system)
  import LazyActorRef._

  @volatile private[this] var _pathStateDoNotCallMeDirectly: AnyRef = _

  @inline
  private def pathState: AnyRef = Unsafe.instance.getObjectVolatile(this, pathStateOffset)

  @inline
  private def updateState(oldState: AnyRef, newState: AnyRef): Boolean =
    Unsafe.instance.compareAndSwapObject(this, pathStateOffset, oldState, newState)

  @inline
  private def setState(newState: AnyRef) {
    Unsafe.instance.putObjectVolatile(this, pathStateOffset, newState)
  }

  override def getParent = provider.tempContainer

  /**
   * Contract of this method:
   * Must always return the same ActorPath, which must have
   * been registered if we haven't been stopped yet.
   */
  @tailrec
  final def path: ActorPath = pathState match {
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

  override def !(message: Any)(implicit sender: ActorRef = null) {
    pathState match {
      case Stopped | _: StoppedWithPath ⇒ provider.deadLetters ! message
      case _                            ⇒ handle(message)
    }
  }

  override def sendSystemMessage(message: SystemMessage) {
    message match {
      case _: Terminate ⇒ stop()
      case _            ⇒
    }
  }

  override def isTerminated = pathState match {
    case Stopped | _: StoppedWithPath ⇒ true
    case _                            ⇒ false
  }

  @tailrec
  final override def stop() {
    pathState match {
      case null ⇒
        // if path was never queried nobody can possibly be watching us, so we don't have to publish termination either
        if (updateState(null, Stopped)) onStop()
        else stop()
      case p: ActorPath ⇒
        if (updateState(p, StoppedWithPath(p))) {
          try {
            onStop()
            provider.deathWatch.publish(Terminated(this))
          } finally {
            unregister(p)
          }
        } else stop()
      case Stopped | _: StoppedWithPath ⇒
      case Registering                  ⇒ stop() // spin until registration is completed before stopping
    }
  }

  // callbacks

  protected def handle(message: Any)(implicit sender: ActorRef)

  protected def onStop() {}

  protected def register(path: ActorPath) {
    provider.registerTempActor(this, path)
  }

  protected def unregister(path: ActorPath) {
    provider.unregisterTempActor(path)
  }
}

object LazyActorRef {
  private val pathStateOffset = Unsafe.instance.objectFieldOffset(
    classOf[LazyActorRef].getDeclaredField("_pathStateDoNotCallMeDirectly"))

  private case object Registering
  private case object Stopped
  private case class StoppedWithPath(path: ActorPath)
}