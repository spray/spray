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

import scala.concurrent.duration.{FiniteDuration, Duration}
import scala.concurrent.{Promise, Await, Future}
import akka.actor.ActorRefFactory
import akka.util.Timeout
import java.util.concurrent.TimeUnit._
import akka.spray.RefUtils


class PimpedFuture[+A](underlying: Future[A]) {

  def await(implicit timeout: Timeout = Duration(1, MINUTES)): A =
    Await.result(underlying, timeout.duration)

  def ready(implicit timeout: Timeout = Duration(1, MINUTES)): Future[A] =
    Await.ready(underlying, timeout.duration)

  def delay(duration: FiniteDuration)(implicit refFactory: ActorRefFactory): Future[A] = {
    val system = RefUtils.actorSystem(refFactory)
    implicit val executionContext = system.dispatcher
    val promise = Promise[A]()
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