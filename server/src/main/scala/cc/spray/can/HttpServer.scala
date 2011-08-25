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
import akka.actor.{PoisonPill, Supervisor, Actor}
import org.slf4j.LoggerFactory

class HttpServer(val config: CanConfig) extends SelectActorComponent with ResponsePreparer {
  private lazy val log = LoggerFactory.getLogger(getClass)
  private lazy val selectActor = Actor.actorOf(new SelectActor)

  def start() {
    log.info("Starting HttpServer with configuration {}", config)
    // start and supervise the selectActor
    Supervisor(
      SupervisorConfig(
        OneForOneStrategy(List(classOf[Exception]), 3, 100),
        List(Supervise(selectActor, Permanent))
      )
    )
  }

  def blockUntilStarted() {
    log.info("Waiting for HttpServer startup to complete...")
    started.await()
  }

  def stop() {
    log.info("Triggering HttpServer shutdown")
    selectActor ! PoisonPill
    selector.wakeup() // the SelectActor is probably blocked at the "selector.select()" call, so wake it up
  }

  def blockUntilStopped() {
    log.info("Waiting for HttpServer shutdown to complete...")
    stopped.await()
  }

}