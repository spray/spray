package cc.spray

import http._

trait Rejection

/**
 * The request was rejected because the HTTP method is unsupported
 */
case class MethodRejection(supported: HttpMethod) extends Rejection

/**
 * The request was rejected because a query parameter was not found
 */
case class QueryParamRequiredRejection(parameterName: String) extends Rejection

/**
 * The request was rejected even though the URI path matched completely.
 */
case object PathMatchedRejection extends Rejection