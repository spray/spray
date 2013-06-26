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

/**
 * An ActorRef which
 * - offers the ability to hook caller-side logic into a `tell`
 * - is registered lazily
 *
 * CAUTION: In order to prevent memory leaks you need to make sure that the
 * ref is explicitly stopped via `stop` in _all_ cases, even if `tell`/bang is never called!
 */
abstract class LazyActorRef(prov: ActorRefProvider) extends UnregisteredActorRefBase(prov) {
  def this(related: ActorRef) = this(RefUtils.provider(related))
  def this(actorRefFactory: ActorRefFactory) = this(RefUtils.provider(actorRefFactory))
  import LazyActorRef._

  @volatile
  private[this] var _watchedByDoNotCallMeDirectly: Set[ActorRef] = ActorCell.emptyActorRefSet

  @inline
  private[this] def watchedBy: Set[ActorRef] = Unsafe.instance.getObjectVolatile(this, watchedByOffset).asInstanceOf[Set[ActorRef]]

  @inline
  private[this] def updateWatchedBy(oldWatchedBy: Set[ActorRef], newWatchedBy: Set[ActorRef]): Boolean =
    Unsafe.instance.compareAndSwapObject(this, watchedByOffset, oldWatchedBy, newWatchedBy)

  @tailrec
  private[this] final def addWatcher(watcher: ActorRef): Boolean = watchedBy match {
    case null  ⇒ false
    case other ⇒ updateWatchedBy(other, other + watcher) || addWatcher(watcher)
  }

  @tailrec
  private[this] final def remWatcher(watcher: ActorRef): Unit = {
    watchedBy match {
      case null  ⇒
      case other ⇒ if (!updateWatchedBy(other, other - watcher)) remWatcher(watcher)
    }
  }

  @tailrec
  private[this] final def clearWatchers(): Set[ActorRef] = watchedBy match {
    case null  ⇒ ActorCell.emptyActorRefSet
    case other ⇒ if (!updateWatchedBy(other, null)) clearWatchers() else other
  }

  override def getParent: InternalActorRef = provider.tempContainer

  override def sendSystemMessage(message: SystemMessage): Unit = {
    message match {
      case _: Terminate ⇒ stop()
      case Watch(watchee, watcher) ⇒
        if (watchee == this && watcher != this) {
          if (!addWatcher(watcher)) watcher ! Terminated(watchee)(existenceConfirmed = true, addressTerminated = false)
        } else System.err.println("BUG: illegal Watch(%s,%s) for %s".format(watchee, watcher, this))
      case Unwatch(watchee, watcher) ⇒
        if (watchee == this && watcher != this) remWatcher(watcher)
        else System.err.println("BUG: illegal Unwatch(%s,%s) for %s".format(watchee, watcher, this))
      case _ ⇒
    }
  }

  // callbacks

  protected override def onStop(): Unit = {
    val watchers = clearWatchers()
    if (!watchers.isEmpty) {
      val termination = Terminated(this)(existenceConfirmed = true, addressTerminated = false)
      watchers foreach { _.tell(termination, this) }
    }
  }

  protected override def register(path: ActorPath): Unit = {
    provider.registerTempActor(this, path)
  }

  protected override def unregister(path: ActorPath): Unit = {
    provider.unregisterTempActor(path)
  }
}

object LazyActorRef {
  private val watchedByOffset = Unsafe.instance.objectFieldOffset(
    classOf[LazyActorRef].getDeclaredField("_watchedByDoNotCallMeDirectly"))
}