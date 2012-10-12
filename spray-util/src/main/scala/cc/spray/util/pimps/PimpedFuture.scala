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

package spray.util.pimps

import akka.util.{Duration, Timeout}
import akka.actor.ActorSystem
import akka.dispatch._


class PimpedFuture[+A](underlying: Future[A]) {

  def await(implicit timeout: Timeout = Duration.Inf): A =
    Await.result(underlying, timeout.duration)

  def ready(implicit timeout: Timeout = Duration.Inf): Future[A] =
    Await.ready(underlying, timeout.duration)

  def delay(duration: Duration)(implicit system: ActorSystem): Future[A] = {
    val promise = Promise[A]()(system.dispatcher)
    underlying.onComplete { value =>
      system.scheduler.scheduleOnce(duration, new Runnable {
        def run() {
          promise.complete(value)
        }
      })
    }
    promise.future
  }
}