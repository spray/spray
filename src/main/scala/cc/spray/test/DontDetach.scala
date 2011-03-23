package cc.spray
package test

import cc.spray.ServiceBuilder
import akka.actor.Actor

trait DontDetach extends ServiceBuilder {
  
  // disable detach-to-actor so we do not have to actually run actors in the test
  override def detached(route: Route)(implicit detachedActorFactory: Route => Actor): Route = route
  
}