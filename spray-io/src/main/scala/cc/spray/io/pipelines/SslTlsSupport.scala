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
package pipelines

import akka.event.LoggingAdapter

object SslTlsSupport {

  def apply(log: LoggingAdapter): PipelineStage = new DoublePipelineStage {

    def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines = new Pipelines {

      def commandPipeline(command: Command) {
        command match {
          case IoPeer.Send(buffers) =>
            // ... perform actual encryption via SslEngine
            val encryptedBuffers = buffers
            // ...
            commandPL(IoPeer.Send(encryptedBuffers))

          case _ => commandPL(command)
        }
      }

      def eventPipeline(event: Event) {
        event match {
          case IoPeer.Received(handle, buffer) =>
            // ... perform actual decryption via SslEngine
            val decryptedBuffer = buffer
            // ...
            eventPL(IoPeer.Received(handle, decryptedBuffer))

          case _ => eventPL(event)
        }
      }
    }
  }
}