/**
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */

package spray.testkit

import akka.actor.{ Terminated, ActorSystem, ActorRef }
import akka.testkit.TestProbe

object TestUtils {

  def verifyActorTermination(actor: ActorRef)(implicit system: ActorSystem): Unit = {
    val watcher = TestProbe()
    watcher.watch(actor)
    assert(watcher.expectMsgType[Terminated].actor == actor)
  }

}

object Specs2Utils {
  def compileOnly[U](block: ⇒ U) = new org.specs2.specification.Scope {}
}