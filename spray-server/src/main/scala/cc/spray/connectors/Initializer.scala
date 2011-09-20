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

package cc.spray.connectors

import akka.actor.BootableActorLoaderService

import javax.servlet.{ServletContextListener, ServletContextEvent}
import akka.util.AkkaLoader

class Initializer extends ServletContextListener {
  lazy val loader = new AkkaLoader
  
  def contextDestroyed(e: ServletContextEvent) {
    loader.shutdown()
  }
  
  def contextInitialized(e: ServletContextEvent) {
    loader.boot(true, new BootableActorLoaderService {})
  }
}
