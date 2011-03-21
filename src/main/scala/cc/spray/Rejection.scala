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
case class MissingQueryParamRejection(parameterName: String) extends Rejection

/**
 * The request was rejected because the requests content-type is unsupported
 */
case class UnsupportedRequestContentTypeRejection(supported: List[ContentTypeRange]) extends Rejection

/**
 * The request was rejected because an message body entity was expected but not supplied 
 */
case object RequestEntityExpectedRejection extends Rejection

/**
 * The request was rejected because the service is not capable of producing a response entity whose
 * content type is accepted by the client
 */
case class UnacceptedResponseContentTypeRejection(supported: List[ContentType]) extends Rejection

/**
 * The request was rejected even though the URI path matched completely.
 */
case object PathMatchedRejection extends Rejection