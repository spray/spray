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

package spray.io

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ConcurrentHashMap
import scala.annotation.tailrec
import akka.actor._
import akka.spray.io.SelectorWakingMailbox


class IOExtension(system: ExtendedActorSystem) extends Extension {
  private[this] val Locked = new AnyRef
  private[this] val mailboxes = new ConcurrentHashMap[ActorRef, SelectorWakingMailbox]
  private[this] val rootBridge = new AtomicReference[AnyRef] // holds either null, Locked or ActorRef
  val settings = new IOBridge.Settings(system.settings.config)

  /**
   * Creates and gets the root IOBridge for the ActorSystem using the systems config settings.
   * If the IOBridge is already constructed simply gets the existing instance.
   */
  def ioBridge: ActorRef = ioBridge(settings)

  /**
   * Creates and gets the root IOBridge for the ActorSystem using the given settings.
   * If the IOBridge is already constructed simply gets the existing instance.
   */
  @tailrec
  final def ioBridge(settings: IOBridge.Settings): ActorRef =
    rootBridge.get match {
      case null if rootBridge.compareAndSet(null, Locked) =>
        var bridge: ActorRef = null
        try bridge = system.actorOf(
          props = Props(new IOBridge(settings)).withDispatcher(IOBridge.DispatcherName),
          name = "io-bridge"
        )
        finally rootBridge.set(bridge)
        bridge
      case null | Locked => ioBridge(settings)
      case bridge: ActorRef => bridge
    }

  ////////////////////// INTERNAL API ////////////////////////

  private[io] def mailboxOf(ref: ActorRef) = Option(mailboxes.get(ref))

  // TODO: mark private[akka] after migration
  def register(actorRef: ActorRef, mailbox: SelectorWakingMailbox) {
    mailboxes.put(actorRef, mailbox)
  }

  // TODO: mark private[akka] after migration
  def unregister(actorRef: ActorRef) {
    mailboxes.remove(actorRef)
  }
}

object IOExtension extends ExtensionId[IOExtension] with ExtensionIdProvider {
  def lookup() = IOExtension
  def createExtension(system: ExtendedActorSystem) = new IOExtension(system)

  private[io] def myMailbox(implicit ctx: ActorContext) = apply(ctx.system).mailboxOf(ctx.self)
}