/*
 * Copyright (C) 2011-2012 spray.cc
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

package cc.spray
package test

import http._
import org.specs2.mutable._
import org.specs2.specification.{Fragments, Step}

abstract class AbstractSprayTest extends Specification with SprayTest with Directives {

  val Ok = HttpResponse(StatusCodes.OK)

  def echoComplete[T]: T => Route = { x => completeWith(x.toString) }

  override def map(fs: => Fragments) = fs ^ Step(actorSystem.shutdown())
}