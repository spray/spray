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

import akka.actor._


trait ConnectionActors extends IOServer {

  var connectionCounter = 0L

  override def createConnection(_key: IOBridge.Key, _tag: Any): Connection =
    new Connection {
      val key = _key
      var tag = _tag
      tag = overrideTag(this)
      private[this] val _handler = createConnectionActor(this) // must be last member to be initialized
      def handler = if (_handler != null) _handler else sys.error("handler not yet available during connection actor creation")
    }

  /**
   * Creates the connection actor ActorRef.
   * CAUTION: this method is called from the constructor of the given Connection.
   * For optimization reasons the `handler` member of the given Connection
   * instance will not yet be initialized (i.e. null). All other members are fully accessible.
   */
  def createConnectionActor(connection: Connection): ActorRef

  /**
   * Creates the actor name for the connection actor for the given connection.
   */
  def nextConnectionActorName: String = {
    connectionCounter += 1
    "c" + connectionCounter
  }

  /**
   * Override to customize the tag for the given connection.
   * By default the connections tag is returned unchanged.
   * CAUTION: this method is called from the constructor of the given Connection.
   * For optimization reasons the `handler` member of the given Connection
   * instance will not yet be initialized (i.e. null). All other members are fully accessible.
   */
  def overrideTag(connection: Connection): Any = connection.tag

  // we assume that we can never recover from failures of a connection actor,
  // we simply kill it, which causes it to close its connection in postStop()
  override def supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy() { case _ => SupervisorStrategy.Stop }

}