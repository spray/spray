package cc.spray

import http._
import org.specs.Specification
import test.RouteTest
import akka.actor.Actor


class ServiceBuilderSpec extends Specification with ServiceBuilder with RouteTest
                         with ServiceBuilderSpec1
                         with ServiceBuilderSpec2 {

  val OK = ResponseContext(Some(HttpResponse()))
  val respondOk: Route = {_.respond(OK)}
  val respondWithUnmatchedPath: Route = {ctx => ctx.respond(ctx.unmatchedPath)}

  // we disable detach-to-actor so we do not have to actually run actors in the test
  override def detached(route: Route)(implicit detachedActorFactory: Route => Actor): Route = route

}