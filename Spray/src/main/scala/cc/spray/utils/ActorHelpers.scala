package cc.spray.utils

import akka.actor.{Actor, ActorRef}

object ActorHelpers {
  
  def actor(id: Symbol): ActorRef = actor(id.toString)

  def actor(id: String): ActorRef = {
    val actors = Actor.registry.actorsFor(id)
    assert(actors.length == 1, actors.length + " actors for id '" + id + "' found, expected exactly one")
    actors.head
  }

  def actor[A <: Actor : Manifest]: ActorRef = {
    val actors = Actor.registry.actorsFor(manifest)
    assert(actors.length == 1, "Actor of type '" + manifest.erasure.getName + "' not found")
    actors.head
  }
  
}