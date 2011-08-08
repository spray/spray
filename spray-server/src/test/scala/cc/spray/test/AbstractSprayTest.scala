package cc.spray
package test

import http._
import org.specs2.mutable._

abstract class AbstractSprayTest extends Specification with SprayTest with Directives {

  val Ok = HttpResponse(StatusCodes.OK)
  val completeOk: Route = { _.complete(Ok) }

  def echoComplete[T]: T => Route = { x => _.complete(x.toString) }

}