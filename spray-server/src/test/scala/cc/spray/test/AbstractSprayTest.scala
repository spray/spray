package cc.spray
package test

import http._
import org.specs2.mutable._
import util.ConsoleLog

abstract class AbstractSprayTest extends Specification with SprayTest with Directives {

  /*implicit def enableLoggingInTests(rootRoute: Route): ServiceTest = new HttpServiceLogic with ServiceTest {
    override lazy val log = new ConsoleLog(this)
    val route = rootRoute
  }*/

  val Ok = HttpResponse(StatusCodes.OK)

  def echoComplete[T]: T => Route = { x => completeWith(x.toString) }

}