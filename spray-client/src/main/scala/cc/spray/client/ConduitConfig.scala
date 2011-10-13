/*
 * Copyright (C) 2011 Mathias Doenitz
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

package cc.spray
package client

import akka.config.Config._

case class ConduitConfig(
  clientActorId: String = "spray-can-client",
  maxConnections: Int = 4,
  pipeliningEnabled: Boolean = false,
  dispatchStrategy: DispatchStrategy = DispatchStrategies.NonPipelining
) {

  require(!clientActorId.isEmpty, "clientActorId must not be empty")

  override def toString =
    "ConduitConfig(\n" +
    "  clientActorId     : " + clientActorId + "\n" +
    "  maxConnections    : " + maxConnections + "\n" +
    "  pipeliningEnabled : " + pipeliningEnabled + "\n" +
    ")"
}

object ConduitConfig {
  lazy val fromAkkaConf = ConduitConfig(
    clientActorId   = config.getString("spray.client.client-actor-id", "spray-can-client"),
    maxConnections  = config.getInt("spray.client.max-connections-per-conduit", 4),
    pipeliningEnabled = config.getBoolean("spray.client.pipelining-enabled", false)
  )
}
