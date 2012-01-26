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

package cc.spray.can
package nio

import akka.actor.Actor
import org.slf4j.Logger
import java.nio.ByteBuffer

trait NioConnectionActorComponent {
  def log: Logger
  def nioWorker: NioWorker

  abstract class NioConnectionActor(val key: Key) extends Actor with Handle { handle =>

    def handler = self

    protected def receive = {
      case x: Closed =>
        log.debug("Stopping connection actor, connection was closed due to {}", x.reason)
        self.stop()

      case x: Received => pipeline {
        new PipelineContext[ByteBuffer, Seq[ByteBuffer]] {
          def input = x.buffer
          def push(output: Any) { nioWorker ! Send(handle, output) }
          def channel = key.channel
          def close() { nioWorker ! Close(handle) }
        }
      }

      case _: CompletedSend => // ignore for now

      case x: CommandError =>
        log.warn("Received {}", x)
    }

    def upstreamPipeline: ByteBuffer => Unit
    def downstreamPipeline: Any => Unit
  }
}