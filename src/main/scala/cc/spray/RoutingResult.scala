package cc.spray

import http.HttpResponse

sealed trait RoutingResult
case class Respond(response: HttpResponse) extends RoutingResult

sealed trait FilterResult[+A]
case class Reject(rejections: Set[Rejection] = Set.empty) extends FilterResult[Nothing] with RoutingResult
case class Pass[+A](values: List[A] = Nil, transform: RequestContext => RequestContext = identity) extends FilterResult[A]

object Reject {
  def apply(rejection: Rejection): Reject = apply(Set(rejection))
}