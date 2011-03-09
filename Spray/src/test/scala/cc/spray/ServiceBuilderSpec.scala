package cc.spray

import http._
import org.specs.Specification
import test.{DetachingDisabled, SprayTest}

class ServiceBuilderSpec extends Specification with ServiceBuilder with SprayTest with DetachingDisabled 
                         with ServiceBuilderSpec1
                         with ServiceBuilderSpec2 {

  val Ok = HttpResponse()
  val respondOk: Route = {_.respond(Ok)}

}