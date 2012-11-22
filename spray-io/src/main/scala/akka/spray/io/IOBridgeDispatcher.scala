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

package akka.spray.io

import java.util.concurrent.TimeUnit
import com.typesafe.config.Config
import akka.util.{FiniteDuration, Duration}
import akka.actor.ActorCell
import akka.dispatch._
import spray.io.IOExtension


class IOBridgeDispatcherConfigurator(config: Config, prerequisites: DispatcherPrerequisites)
  extends MessageDispatcherConfigurator(config, prerequisites) {

  override def dispatcher(): MessageDispatcher =
    new IOBridgeDispatcher(
      _prerequisites = prerequisites,
      _id = config.getString("id"),
      _mailboxType = mailboxType(),
      _shutdownTimeout = Duration(config.getMilliseconds("shutdown-timeout"), TimeUnit.MILLISECONDS),
      _threadPoolConfig = ThreadPoolConfig() // we use the default config
    )
}

class IOBridgeDispatcher(
  _prerequisites: DispatcherPrerequisites,
  _id: String,
  _mailboxType: MailboxType,
  _shutdownTimeout: FiniteDuration,
  _threadPoolConfig: ThreadPoolConfig
  ) extends PinnedDispatcher(_prerequisites, null,  _id, _mailboxType, _shutdownTimeout, _threadPoolConfig) {

  override protected[akka] def createMailbox(actor: ActorCell) = {
    val mb = new SelectorWakingMailbox(actor, mailboxType.create(Some(actor)))
    IOExtension(actor.system).register(actor.self, mb)
    mb
  }

  protected[akka] override def unregister(actor: ActorCell) {
    super.unregister(actor)
    IOExtension(actor.system).unregister(actor.self)
  }
}
