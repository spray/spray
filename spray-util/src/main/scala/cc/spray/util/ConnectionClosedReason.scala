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

package spray.util


// generally available trait modelling "connection closed" events,
// defined here in spray-util because spray-util is the only module that is
// available to all other spray modules (except spray-http) and we can this way
// use this trait in spray-io, spray-servlet and spray-httpx
trait IOClosed {
  def reason: ConnectionClosedReason
}

/**
 * Simple sum type modelling the various reasons for why a connection was closed.
 */
sealed trait ConnectionClosedReason

/**
 * The connection was actively and cleanly closed
 * after all pending writes had been flushed.
 */
case object CleanClose extends ConnectionClosedReason

/**
 * The connection was closed by the peer.
 */
case object PeerClosed extends ConnectionClosedReason

/**
 * The connection was closed due to an idle timeout on the connection.
 */
case object IdleTimeout extends ConnectionClosedReason

/**
 * The connection was closed due to a request not having been responded to in a timely fashion.
 */
case object RequestTimeout extends ConnectionClosedReason

/**
 * The connection was closed because the peer did not adhere to
 * the higher-level protocol.
 */
case class ProtocolError(msg: String) extends ConnectionClosedReason

/**
 * The connection was closed due to an IO error.
 */
case class IOError(error: Throwable) extends ConnectionClosedReason



































