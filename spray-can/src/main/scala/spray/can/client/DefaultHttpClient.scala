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

package spray.can.client

import java.util.concurrent.atomic.AtomicReference
import akka.actor._
import akka.spray.ExtensionActorRef
import spray.io._


object DefaultHttpClient extends ExtensionId[ExtensionActorRef] with ExtensionIdProvider {
  private type ClientConfig = (ClientSettings, PipelineContext => Boolean, ClientSSLEngineProvider)
  private[this] val config = new AtomicReference[ClientConfig]

  def lookup() = DefaultHttpClient

  def apply(system: ActorSystem,
            settings: ClientSettings = null,
            sslEnabled: PipelineContext => Boolean = HttpClient.DefaultSslEnabled)
           (implicit sslEngineProvider: ClientSSLEngineProvider): ActorRef with Extension = {
    val clientSettings = Option(settings).getOrElse(ClientSettings(system.settings.config))
    if (!config.compareAndSet(null, (clientSettings, sslEnabled, sslEngineProvider)))
      throw new IllegalStateException("Settings can only be supplied on the first call to DefaultHttpClient.apply")
    apply(system)
  }

  def createExtension(system: ExtendedActorSystem) = {
    val (settings, sslEnabled, sslEngineProvider) = Option(config.get).getOrElse {
      (ClientSettings(system.settings.config), HttpClient.DefaultSslEnabled, implicitly[ClientSSLEngineProvider])
    }
    val client = system.actorOf(
      props = Props(new HttpClient(IOExtension(system).ioBridge(), settings, sslEnabled)(sslEngineProvider)),
      name = "default-http-client"
    )
    new ExtensionActorRef(client)
  }
}