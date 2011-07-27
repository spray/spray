/*
 * Copyright (C) 2011 Mathias Doenitz
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

package cc.spray.utils

import akka.dispatch.Future
import akka.actor.Scheduler
import java.util.concurrent.TimeUnit

class PimpedFuture[F <: Future[_]](future: F) {

  /**
   * Attaches a timeout handler to the given future, which is executed shortly after the future has timed out
   * if it hasn't already been completed at that point in time.
   * Note that this implementation has the following limitations:
   * - the callback is called at X ms after the call to "onTimeout", not (as it should be) at X ms after future
   *   creation
   * - the timeout callback is run on the (single!) scheduler thread created by the akka.dispatch.Scheduler (unless
   *   the future is already expired at the time of call) and should therefore be non-blocking and fast
   * Akka 1.2 will supply this functionality by itself, so this pimp will be removed at some point.
   */
  def onTimeout(callback: F => Unit): F = {
    if (!future.isCompleted) {
      if (!future.isExpired) {
        val runnable = new Runnable {
          def run() {
            if (!future.isCompleted) callback(future)
          }
        }
        Scheduler.scheduleOnce(runnable, future.timeoutInNanos, TimeUnit.NANOSECONDS)
      } else callback(future)
    }
    future
  }
  
}