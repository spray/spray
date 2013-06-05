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
import akka.dispatch.SystemMessage

// enables an ActorRef to take on the role of an Akka Extension
// useful for easy management of long-lived actor "singletons",
// i.e. actors that only exist once per ActorSystem
class ExtensionActorRef(underlying: ActorRef) extends InternalActorRef with ActorRefScope with Extension {
  private[this] val delegate = RefUtils.asInternalActorRef(underlying)
  def resume() = delegate.resume()
  def suspend() = delegate.suspend()
  def restart(cause: Throwable) = delegate.restart(cause)
  def stop() = delegate.stop()
  def sendSystemMessage(message: SystemMessage) = delegate.sendSystemMessage(message)
  def provider: ActorRefProvider = delegate.provider
  def getParent: InternalActorRef = delegate.getParent
  def getChild(name: Iterator[String]) = delegate.getChild(name)
  def isLocal = delegate.isLocal
  def path = delegate.path
  def isTerminated = delegate.isTerminated
  def !(message: Any)(implicit sender: ActorRef) = delegate ! message
}
