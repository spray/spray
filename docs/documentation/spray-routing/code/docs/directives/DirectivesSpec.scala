package docs.directives

import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import spray.routing.HttpService

abstract class DirectivesSpec extends Specification with Specs2RouteTest with HttpService {
  def actorRefFactory = system
}
