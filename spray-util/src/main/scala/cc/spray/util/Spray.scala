package cc.spray.util

import akka.actor.ActorSystem
import java.util.concurrent.atomic.AtomicReference
import annotation.tailrec

/**
 * For spray-1.0-M1 we use a global actor system for all spray-client and spray-server stuff,
 * since we'd like to port the spray 0.9 code to Akka 2.0 with as few changes as possible.
 * This global ActorSystem is going to go away with 1.0-M2 when we have an architecture that
 * was built from the ground up for Akka 2.0.
 */
object Spray {
  private[this] val _system = new AtomicReference[ActorSystem]

  def set(system: ActorSystem): Boolean = _system.compareAndSet(null, system)

  @tailrec
  def system: ActorSystem = _system.get match {
    case null => set(ActorSystem("spray")); system
    case s => s
  }
}
