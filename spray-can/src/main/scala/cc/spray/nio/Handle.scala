package cc.spray.nio

import akka.actor.ActorRef

trait Handle {
  def key: Key

  def handler: ActorRef
}