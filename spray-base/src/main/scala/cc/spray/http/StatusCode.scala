/*
 * Copyright (C) 2011 Mathias Doenitz
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team (http://github.com/jdegoes/blueeyes)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cc.spray.http

sealed abstract class StatusCode {
  def value: Int
  def defaultMessage: String
  def isSuccess: Boolean
  def isWarning: Boolean
  def isFailure: Boolean
  override def toString = "StatusCode(" + value + ", " + defaultMessage + ')'

  StatusCodes.register(this, value)
}

object StatusCode {
  import StatusCodes._
  implicit def int2StatusCode(code: Int): StatusCode = getForKey(code).getOrElse(InternalServerError)
}

sealed abstract class HttpSuccess extends StatusCode {
  def isSuccess = true
  def isWarning = false
  def isFailure = false
}
sealed abstract class HttpWarning extends StatusCode {
  def isSuccess = false
  def isWarning = true
  def isFailure = false
}
sealed abstract class HttpFailure extends StatusCode {
  def isSuccess = false
  def isWarning = false
  def isFailure = true
}
object HttpFailure {
  import StatusCodes._
  implicit def int2HttpFailure(code: Int): HttpFailure = getForKey(code) match {
    case x: HttpFailure => x
    case _ => InternalServerError
  }
}

object StatusCodes extends ObjectRegistry[Int, StatusCode] {
  class Informational private[StatusCodes] (val value: Int, val defaultMessage: String) extends HttpSuccess
  class Success       private[StatusCodes] (val value: Int, val defaultMessage: String) extends HttpSuccess
  class Redirection   private[StatusCodes] (val value: Int, val defaultMessage: String, val htmlTemplate: String) extends HttpSuccess
  class Warning       private[StatusCodes] (val value: Int, val defaultMessage: String) extends HttpWarning
  class ClientError   private[StatusCodes] (val value: Int, val defaultMessage: String) extends HttpFailure
  class ServerError   private[StatusCodes] (val value: Int, val defaultMessage: String) extends HttpFailure
  
  val Continue                     = new Informational(100, "The server has received the request headers, and the client should proceed to send the request body.")
  val SwitchingProtocols           = new Informational(101, "The server is switching protocols, because the client requested the switch.")
  val Processing                   = new Informational(102, "The server is processing the request, but no response is available yet.")
                                     
  val Response                     = new Warning(110, "The returned response is stale.")
  val Revalidation                 = new Warning(111, "A cache returned a stale response because an attempt to revalidate the response failed, due to an inability to reach the server.")
  val Disconnected                 = new Warning(112, "The cache was intentionally disconnected from the rest of the network for a period of time.")
  val Heuristic                    = new Warning(113, "The cache heuristically chose a freshness lifetime greater than 24 hours and the response's age was greater than 24 hours.")
  val Miscellaneous                = new Warning(119, "Miscellaneous warning.")
  val Transformation               = new Warning(214, "A transformation changed the content-coding of the response, or the entity-body of the response.")
  val MiscellaneousPersistent      = new Warning(299, "Miscellaneous persistent warning.")

  val OK                           = new Success(200, "OK")
  val Created                      = new Success(201, "The request has been fulfilled and resulted in a new resource being created.")
  val Accepted                     = new Success(202, "The request has been accepted for processing, but the processing has not been completed.")
  val NonAuthoritativeInformation  = new Success(203, "The server successfully processed the request, but is returning information that may be from another source.")
  val NoContent                    = new Success(204, "The server successfully processed the request, but is not returning any content.")
  val ResetContent                 = new Success(205, "The server successfully processed the request, but is not returning any content.")
  val PartialContent               = new Success(206, "The server is delivering only part of the resource due to a range header sent by the client.")
  val Multi                        = new Success(207, "The message body that follows is an XML message and can contain a number of separate response codes, depending on how many sub-requests were made.")

  val MultipleChoices              = new Redirection(300, "There are multiple options for the resource that the client may follow.", "There are multiple options for the resource that the client may follow. The preferred one is <a href=\"%s\">this URI</a>.")
  val MovedPermanently             = new Redirection(301, "This and all future requests should be directed to the given URI.", "This and all future requests should be directed to <a href=\"%s\">this URI</a>.")
  val Found                        = new Redirection(302, "The resource was found, but at a different URI.", "The requested resource temporarily resides under <a href=\"%s\">this URI</a>.")
  val SeeOther                     = new Redirection(303, "The response to the request can be found under another URI using a GET method.", "The response to the request can be found under <a href=\"%s\">this URI</a> using a GET method.")
  val NotModified                  = new Redirection(304, "The resource has not been modified since last requested.", "")
  val UseProxy                     = new Redirection(305, "This single request is to be repeated via the proxy given by the Location field.", "This single request is to be repeated via the proxy under <a href=\"%s\">this URI</a>.")
  val TemporaryRedirect            = new Redirection(307, "The request should be repeated with another URI, but future requests can still use the original URI.", "The request should be repeated with <a href=\"%s\">this URI</a>, but future requests can still use the original URI.")

  val BadRequest                   = new ClientError(400, "The request contains bad syntax or cannot be fulfilled.")
  val Unauthorized                 = new ClientError(401, "Authentication is possible but has failed or not yet been provided.")
  val PaymentRequired              = new ClientError(402, "Reserved for future use.")
  val Forbidden                    = new ClientError(403, "The request was a legal request, but the server is refusing to respond to it.")
  val NotFound                     = new ClientError(404, "The requested resource could not be found but may be available again in the future.")
  val MethodNotAllowed             = new ClientError(405, "A request was made of a resource using a request method not supported by that resource;")
  val NotAcceptable                = new ClientError(406, "The requested resource is only capable of generating content not acceptable according to the Accept headers sent in the request.")
  val ProxyAuthenticationRequired  = new ClientError(407, "Proxy authentication is required to access the requested resource.")
  val RequestTimeout               = new ClientError(408, "The server timed out waiting for the request.")
  val Conflict                     = new ClientError(409, "The request could not be processed because of conflict in the request, such as an edit conflict.")
  val Gone                         = new ClientError(410, "The resource requested is no longer available and will not be available again.")
  val LengthRequired               = new ClientError(411, "The request did not specify the length of its content, which is required by the requested resource.")
  val PreconditionFailed           = new ClientError(412, "The server does not meet one of the preconditions that the requester put on the request.")
  val RequestEntityTooLarge        = new ClientError(413, "The request is larger than the server is willing or able to process.")
  val RequestTooLong               = new ClientError(414, "The URI provided was too long for the server to process.")
  val UnsupportedMediaType         = new ClientError(415, "The request entity has a media type which the server or resource does not support.")
  val RequestedRangeNotSatisfiable = new ClientError(416, "The client has asked for a portion of the file, but the server cannot supply that portion.")
  val ExpectationFailed            = new ClientError(417, "The server cannot meet the requirements of the Expect request-header field.")
  val TooManyConnections           = new ClientError(421, "There are too many connections from your internet address.")
  val UnprocessableEntity          = new ClientError(422, "The request was well-formed but was unable to be followed due to semantic errors.")
  val Locked                       = new ClientError(423, "The resource that is being accessed is locked.")
  val FailedDependency             = new ClientError(424, "The request failed due to failure of a previous request.")
  val UnorderedCollection          = new ClientError(425, "The collection is unordered.")
  val UpgradeRequired              = new ClientError(426, "The client should switch to a different protocol such as TLS/1.0.")
  val RetryWith                    = new ClientError(449, "The request should be retried after doing the appropriate action.")

  val InternalServerError          = new ServerError(500, "There was an internal server error.")
  val NotImplemented               = new ServerError(501, "The server either does not recognize the request method, or it lacks the ability to fulfill the request.")
  val BadGateway                   = new ServerError(502, "The server was acting as a gateway or proxy and received an invalid response from the upstream server.")
  val ServiceUnavailable           = new ServerError(503, "The server is currently unavailable (because it is overloaded or down for maintenance).")
  val GatewayTimeout               = new ServerError(504, "The server was acting as a gateway or proxy and did not receive a timely request from the upstream server.")
  val HTTPVersionNotSupported      = new ServerError(505, "The server does not support the HTTP protocol version used in the request.")
  val VariantAlsoNegotiates        = new ServerError(506, "Transparent content negotiation for the request, results in a circular reference.")
  val InsufficientStorage          = new ServerError(507, "Insufficient storage to complete the request.")
  val BandwidthLimitExceeded       = new ServerError(509, "Bandwidth limit has been exceeded.")
  val NotExtended                  = new ServerError(510, "Further extensions to the request are required for the server to fulfill it.")
  val UserAccessDenied             = new ServerError(530, "User access is denied to the specified resource.")
}
