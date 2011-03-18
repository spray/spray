package cc.spray

import http.HttpResponse

sealed trait RoutingResult
case class Respond(response: HttpResponse) extends RoutingResult

sealed trait FilterResult
case class Reject(rejections: Set[Rejection] = Set.empty) extends FilterResult with RoutingResult
case class Pass(values: List[String] = Nil, transform: RequestContext => RequestContext = identity) extends FilterResult

object Reject {
  def apply(rejection: Rejection): Reject = apply(Set(rejection))
}