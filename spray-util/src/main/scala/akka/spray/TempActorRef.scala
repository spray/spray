/*
 * Copyright (C) 2011, 2012 Mathias Doenitz
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

import akka.actor.{InternalActorRef, ActorRef}

/**
 * We need access to a minimal wrapper ActorRef in order to transparently associate messages with
 * their replies. The akka.actor.MinimalActorRef is perfect for this but, unfortunately, is marked
 * `private[akka]`, which is why we need to "expose" it here.
 */
abstract class TempActorRef(related: ActorRef) extends akka.actor.MinimalActorRef {
  lazy val path = provider.tempPath()
  def provider = related.asInstanceOf[InternalActorRef].provider
}


