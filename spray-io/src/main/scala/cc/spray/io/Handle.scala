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

import akka.actor.ActorRef
import java.net.InetSocketAddress

//# source-quote
/**
 * A handle for a network connection.
 */
trait Handle {
  /**
   * The key identifying the network connection.
   */
  def key: Key

  /**
   * The actor handling events coming in from the network.
   */
  def handler: ActorRef

  /**
   * The remote address this connection is attached to.
   */
  def remoteAddress: InetSocketAddress

  /**
   * The local address this connection is attached to.
   */
  def localAddress: InetSocketAddress

  /**
   * The ActorRef that originally commanded the establishment of this connection.
   * In the case of a client connection the sender of the `Connect` command,
   * in the case of a server connection the sender of the `Bind` command.
   */
  def commander: ActorRef

  /**
   * A custom, application-defined tag object that can be attached to a Bind or
   * Connect command and is made available to the application through the handle.
   * It is not used for any purpose inside of spray-io itself.
   */
  def tag: Any
}
//#

case class SimpleHandle(
  key: Key,
  handler: ActorRef,
  remoteAddress: InetSocketAddress,
  localAddress: InetSocketAddress,
  commander: ActorRef,
  tag: Any
) extends Handle