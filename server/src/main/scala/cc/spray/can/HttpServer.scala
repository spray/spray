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

package cc.spray.can

import akka.config.Supervision._
import org.slf4j.LoggerFactory
import akka.actor.{Kill, Supervisor, Actor}
import akka.dispatch.{AlreadyCompletedFuture, Future}

class HttpServer(val config: CanConfig) extends SelectActorComponent with ResponsePreparer {
  private lazy val log = LoggerFactory.getLogger(getClass)
  private lazy val selectActor = Actor.actorOf(new SelectActor)

  def start(): Future[Unit] = {
    if (selectActor.isUnstarted) {
      log.info("Starting HttpServer with configuration {}", config)
      // start and supervise the selectActor
      Supervisor(
        SupervisorConfig(
          OneForOneStrategy(List(classOf[Exception]), 3, 100),
          List(Supervise(selectActor, Permanent))
        )
      )
      (selectActor ? 'start).mapTo[Unit]
    } else {
      log.warn("Cannot start an already running HttpServer")
      new AlreadyCompletedFuture(Right(()))
    }
  }

  def stop(): Future[Unit] = {
    if (selectActor.isShutdown) {
      log.warn("Cannot stop an already stopped HttpServer")
      new AlreadyCompletedFuture(Right(()))
    } else {
      log.info("Triggering HttpServer shutdown")
      val future = (selectActor ? 'stop).mapTo[Unit]
      selector.wakeup() // the SelectActor is probably blocked at the "selector.select()" call, so wake it up
      future
    }
  }

  def reset() {
    selectActor ! Kill
  }
}