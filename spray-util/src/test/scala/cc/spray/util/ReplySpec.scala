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

package cc.spray.util

import org.specs2.mutable.Specification
import akka.actor.{Props, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Duration

class ReplySpec(_system: ActorSystem) extends TestKit(_system) with Specification with ImplicitSender {
  def this() = this(ActorSystem())

  args(sequential = true)

  val echoRef = system.actorOf(Props(behavior = ctx => { case x => ctx.sender ! x }))

  "The Reply" should {
    "be able to inject itself into a reply message when unregistered" in {
      val contextRef = Reply.withContext(42)
      echoRef.tell('Yeah, contextRef)
      receiveOne(Duration("1 second")).asInstanceOf[Reply].copy(contextRef = null) === Reply('Yeah, 42, null)
    }
    "be able to inject itself into a reply message when registered" in {
      val contextRef = Reply.withContext(43)
      contextRef.hashCode // trigger registration
      echoRef.tell('Yeah, contextRef)
      val received = receiveOne(Duration("1 second"))
      contextRef.stop() // unregister
      received === Reply('Yeah, 43, contextRef)
    }
  }

}
