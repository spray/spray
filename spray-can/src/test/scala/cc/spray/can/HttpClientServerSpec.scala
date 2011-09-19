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

import org.specs2._
import specification.Step
import akka.actor.Actor

class HttpClientServerSpec extends Specification with HttpClientSpecs with HttpServerSpecs {

  // we need to merge the client and server tests into one spec in order to be able to have one
  // common stop step for shutting down all actors

  def is =
    sequential^
    clientSpecs^
    p^
    //serverSpecs^
    Step(Actor.registry.shutdownAll())

}
