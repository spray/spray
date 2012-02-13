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

package cc.spray.io

import java.nio.ByteBuffer

sealed trait Event

// "general" events not on the connection-level
case object Stopped extends Event
case class Bound(bindingKey: Key) extends Event
case class Unbound(bindingKey: Key) extends Event
case class Connected(key: Key, tag: Any = ()) extends Event

// connection-level events
case class Closed(handle: Handle, reason: ConnectionClosedReason) extends Event
case class CompletedSend(handle: Handle) extends Event
case class Received(handle: Handle, buffer: ByteBuffer) extends Event

case class CommandError(command: Command, error: Throwable) extends Event