/*
 * Copyright © 2011-2013 the spray project <http://spray.io>
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

import scala.concurrent.duration.Duration
import akka.io.Tcp
import spray.util.{ Timestamp, requirePositive }
import akka.io.Tcp._
import akka.util.ByteString

/**
 * A pipeline stage that will abort a connection after an idle timeout has elapsed.
 * The idle timer is not exact but will abort the connection earliest when the timeout
 * has passed after these events:
 *   - the last Tcp.Received message was received
 *   - no Write was pending according to an empty test write sent after the last Write
 *   - a new timeout was set
 */
object ConnectionTimeouts {

  def apply(idleTimeout: Duration): PipelineStage = {
    requirePositive(idleTimeout)

    new PipelineStage {
      def apply(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines = new DynamicPipelines { outer ⇒
        var timeout = idleTimeout
        var idleDeadline = Timestamp.never
        def resetDeadline() = idleDeadline = Timestamp.now + timeout

        become(atWork(writePossiblyPending = false))

        def atWork(writePossiblyPending: Boolean): State = new State {
          resetDeadline()
          val commandPipeline: CPL = {
            case write: Tcp.WriteCommand ⇒
              commandPL(write)
              become(atWork(writePossiblyPending = true))
            case SetIdleTimeout(newTimeout) ⇒
              timeout = newTimeout; resetDeadline()
            case cmd ⇒ commandPL(cmd)
          }
          val eventPipeline: EPL = {
            case x: Tcp.Received ⇒
              resetDeadline(); eventPL(x)
            case tick @ TickGenerator.Tick ⇒
              if (idleDeadline.isPast && writePossiblyPending) become(checkForPendingWrite())
              else shutdownIfIdle()

              eventPL(tick)

            case CommandFailed(TestWrite) | NoWritePending ⇒ // ignore
            case ev                                        ⇒ eventPL(ev)
          }
        }
        def checkForPendingWrite(): State = new State {
          resetDeadline()
          commandPL(TestWrite)

          def commandPipeline = {
            case write: Tcp.WriteCommand ⇒
              become(atWork(writePossiblyPending = true)); outer.commandPipeline(write)
            case SetIdleTimeout(newTimeout) ⇒
              timeout = newTimeout; resetDeadline()
            case cmd ⇒ commandPL(cmd)
          }
          def eventPipeline = {
            // case Tcp.Received not necessary because we are just waiting for the TestWrite acknowledgements in this state.
            // Receiving the acknowledgement will reset the timeout in any case.
            case CommandFailed(TestWrite) ⇒ become(atWork(writePossiblyPending = true)) // there's a write still pending
            case NoWritePending           ⇒ become(atWork(writePossiblyPending = false))
            case tick @ TickGenerator.Tick ⇒ // happens only if connection actor is too busy to react
              shutdownIfIdle()
              eventPL(tick)
            case ev ⇒ eventPL(ev)
          }
        }

        def shutdownIfIdle(): Unit =
          if (idleDeadline.isPast) {
            context.log.debug("Closing connection due to idle timeout...")
            commandPL(Tcp.Abort)
          }
      }
    }
  }

  ////////////// COMMANDS //////////////

  case class SetIdleTimeout(timeout: Duration) extends Command {
    requirePositive(timeout)
  }

  private[io] case object NoWritePending extends Event
  private[io] val TestWrite = Tcp.Write(ByteString.empty, NoWritePending)
}
