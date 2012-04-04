package cc.spray
package test

import http._
import org.specs2.mutable._
import org.specs2.specification.{Fragments, Step}

abstract class AbstractSprayTest extends Specification with SprayTest with Directives {

  val Ok = HttpResponse(StatusCodes.OK)

  def echoComplete[T]: T => Route = { x => completeWith(x.toString) }

  override def map(fs: => Fragments) = fs ^ Step(system.shutdown())
}