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

object RefUtils {

  def provider(ref: ActorRef): ActorRefProvider =
    asInternalActorRef(ref).provider

  def provider(actorRefFactory: ActorRefFactory): ActorRefProvider =
    actorRefFactory match {
      case x: ActorContext        ⇒ provider(x.system)
      case x: ExtendedActorSystem ⇒ x.provider
      case x: ActorSystem         ⇒ throw new IllegalArgumentException("Unsupported ActorSystem implementation: " + x)
    }

  def isLocal(ref: ActorRef): Boolean =
    asInternalActorRef(ref).isLocal

  private[akka] def asInternalActorRef(ref: ActorRef): InternalActorRef =
    ref match {
      case x: InternalActorRef ⇒ x
      case x                   ⇒ throw new IllegalArgumentException("Unsupported ActorRef " + x)
    }

  def actorSystem(refFactory: ActorRefFactory): ExtendedActorSystem =
    refFactory match {
      case x: ActorContext        ⇒ actorSystem(x.system)
      case x: ExtendedActorSystem ⇒ x
      case x                      ⇒ throw new IllegalArgumentException("Unsupported ActorRefFactory implementation:" + refFactory)
    }

}
