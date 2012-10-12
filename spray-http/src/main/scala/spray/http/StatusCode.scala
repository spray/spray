/*
 * Copyright (C) 2011-2012 spray.io
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

package spray.http

sealed abstract class StatusCode {
  def value: Int
  def reason: String
  def defaultMessage: String
  def isSuccess: Boolean
  def isFailure: Boolean
  override def toString = "StatusCode(" + value + ", " + reason + ')'
  def formatPretty = value.toString + ' ' + reason

  StatusCodes.register(this, value)
}

object StatusCode {
  import StatusCodes._
  implicit def int2StatusCode(code: Int): StatusCode = getForKey(code).getOrElse(InternalServerError)
}

sealed abstract class HttpSuccess extends StatusCode {
  def isSuccess = true
  def isFailure = false
}
sealed abstract class HttpFailure extends StatusCode {
  def isSuccess = false
  def isFailure = true
}

object StatusCodes extends ObjectRegistry[Int, StatusCode] {
  class Informational private[StatusCodes] (val value: Int, val reason: String, val defaultMessage: String) extends HttpSuccess
  class Success       private[StatusCodes] (val value: Int, val reason: String, val defaultMessage: String) extends HttpSuccess
  class Redirection   private[StatusCodes] (val value: Int, val reason: String, val defaultMessage: String, val htmlTemplate: String) extends HttpSuccess
  class ClientError   private[StatusCodes] (val value: Int, val reason: String, val defaultMessage: String) extends HttpFailure
  class ServerError   private[StatusCodes] (val value: Int, val reason: String, val defaultMessage: String) extends HttpFailure
  
  val Continue           = new Informational(100, "Continue", "The server has received the request headers, and the client should proceed to send the request body.")
  val SwitchingProtocols = new Informational(101, "Switching Protocols", "The server is switching protocols, because the client requested the switch.")
  val Processing         = new Informational(102, "Processing", "The server is processing the request, but no response is available yet.")
                                     
  val OK                          = new Success(200, "OK", "OK")
  val Created                     = new Success(201, "Created", "The request has been fulfilled and resulted in a new resource being created.")
  val Accepted                    = new Success(202, "Accepted", "The request has been accepted for processing, but the processing has not been completed.")
  val NonAuthoritativeInformation = new Success(203, "Non-Authoritative Information", "The server successfully processed the request, but is returning information that may be from another source.")
  val NoContent                   = new Success(204, "No Content", "The server successfully processed the request, but is not returning any content.")
  val ResetContent                = new Success(205, "Reset Content", "The server successfully processed the request, but is not returning any content.")
  val PartialContent              = new Success(206, "Partial Content", "The server is delivering only part of the resource due to a range header sent by the client.")
  val MultiStatus                 = new Success(207, "Multi-Status", "The message body that follows is an XML message and can contain a number of separate response codes, depending on how many sub-requests were made.")
  val AlreadyReported             = new Success(208, "Already Reported", "The members of a DAV binding have already been enumerated in a previous reply to this request, and are not being included again.")
  val IMUsed                      = new Success(226, "IM Used", "The server has fulfilled a GET request for the resource, and the response is a representation of the result of one or more instance-manipulations applied to the current instance.")

  val MultipleChoices   = new Redirection(300, "Multiple Choices", "There are multiple options for the resource that the client may follow.", "There are multiple options for the resource that the client may follow. The preferred one is <a href=\"%s\">this URI</a>.")
  val MovedPermanently  = new Redirection(301, "Moved Permanently", "This and all future requests should be directed to the given URI.", "This and all future requests should be directed to <a href=\"%s\">this URI</a>.")
  val Found             = new Redirection(302, "Found", "The resource was found, but at a different URI.", "The requested resource temporarily resides under <a href=\"%s\">this URI</a>.")
  val SeeOther          = new Redirection(303, "See Other", "The response to the request can be found under another URI using a GET method.", "The response to the request can be found under <a href=\"%s\">this URI</a> using a GET method.")
  val NotModified       = new Redirection(304, "Not Modified", "The resource has not been modified since last requested.", "")
  val UseProxy          = new Redirection(305, "Use Proxy", "This single request is to be repeated via the proxy given by the Location field.", "This single request is to be repeated via the proxy under <a href=\"%s\">this URI</a>.")
  val TemporaryRedirect = new Redirection(307, "Temporary Redirect", "The request should be repeated with another URI, but future requests can still use the original URI.", "The request should be repeated with <a href=\"%s\">this URI</a>, but future requests can still use the original URI.")
  val PermanentRedirect = new Redirection(308, "Permanent Redirect", "The request, and all future requests should be repeated using another URI.", "The request, and all future requests should be repeated using <a href=\"%s\">this URI</a>.")

  val BadRequest                   = new ClientError(400, "Bad Request", "The request contains bad syntax or cannot be fulfilled.")
  val Unauthorized                 = new ClientError(401, "Unauthorized", "Authentication is possible but has failed or not yet been provided.")
  val PaymentRequired              = new ClientError(402, "Payment Required", "Reserved for future use.")
  val Forbidden                    = new ClientError(403, "Forbidden", "The request was a legal request, but the server is refusing to respond to it.")
  val NotFound                     = new ClientError(404, "Not Found", "The requested resource could not be found but may be available again in the future.")
  val MethodNotAllowed             = new ClientError(405, "Method Not Allowed", "A request was made of a resource using a request method not supported by that resource;")
  val NotAcceptable                = new ClientError(406, "Not Acceptable", "The requested resource is only capable of generating content not acceptable according to the Accept headers sent in the request.")
  val ProxyAuthenticationRequired  = new ClientError(407, "Proxy Authentication Required", "Proxy authentication is required to access the requested resource.")
  val RequestTimeout               = new ClientError(408, "Request Timeout", "The server timed out waiting for the request.")
  val Conflict                     = new ClientError(409, "Conflict", "The request could not be processed because of conflict in the request, such as an edit conflict.")
  val Gone                         = new ClientError(410, "Gone", "The resource requested is no longer available and will not be available again.")
  val LengthRequired               = new ClientError(411, "Length Required", "The request did not specify the length of its content, which is required by the requested resource.")
  val PreconditionFailed           = new ClientError(412, "Precondition Failed", "The server does not meet one of the preconditions that the requester put on the request.")
  val RequestEntityTooLarge        = new ClientError(413, "Request Entity Too Large", "The request is larger than the server is willing or able to process.")
  val RequestUriTooLong            = new ClientError(414, "Request-URI Too Long", "The URI provided was too long for the server to process.")
  val UnsupportedMediaType         = new ClientError(415, "Unsupported Media Type", "The request entity has a media type which the server or resource does not support.")
  val RequestedRangeNotSatisfiable = new ClientError(416, "Requested Range Not Satisfiable", "The client has asked for a portion of the file, but the server cannot supply that portion.")
  val ExpectationFailed            = new ClientError(417, "Expectation Failed", "The server cannot meet the requirements of the Expect request-header field.")
  val EnhanceYourCalm              = new ClientError(420, "Enhance Your Calm", "You are being rate-limited.") // Twitter only
  val UnprocessableEntity          = new ClientError(422, "Unprocessable Entity", "The request was well-formed but was unable to be followed due to semantic errors.")
  val Locked                       = new ClientError(423, "Locked", "The resource that is being accessed is locked.")
  val FailedDependency             = new ClientError(424, "Failed Dependency", "The request failed due to failure of a previous request.")
  val UnorderedCollection          = new ClientError(425, "Unordered Collection", "The collection is unordered.")
  val UpgradeRequired              = new ClientError(426, "Upgrade Required", "The client should switch to a different protocol.")
  val PreconditionRequired         = new ClientError(428, "Precondition Required", "The server requires the request to be conditional.")
  val TooManyRequests              = new ClientError(429, "Too Many Requests", "The user has sent too many requests in a given amount of time.")
  val RequestHeaderFieldsTooLarge  = new ClientError(431, "Request Header Fields Too Large", "The server is unwilling to process the request because either an individual header field, or all the header fields collectively, are too large.")
  val RetryWith                    = new ClientError(449, "Retry With", "The request should be retried after doing the appropriate action.")
  val BlockedByParentalControls    = new ClientError(450, "Blocked by Windows Parental Controls", "Windows Parental Controls are turned on and are blocking access to the given webpage.")
  val UnavailableForLegalReasons   = new ClientError(451, "Unavailable For Legal Reasons", "Resource access is denied for legal reasons.")

  val InternalServerError           = new ServerError(500, "Internal Server Error", "There was an internal server error.")
  val NotImplemented                = new ServerError(501, "Not Implemented", "The server either does not recognize the request method, or it lacks the ability to fulfill the request.")
  val BadGateway                    = new ServerError(502, "Bad Gateway", "The server was acting as a gateway or proxy and received an invalid response from the upstream server.")
  val ServiceUnavailable            = new ServerError(503, "Service Unavailable", "The server is currently unavailable (because it is overloaded or down for maintenance).")
  val GatewayTimeout                = new ServerError(504, "Gateway Timeout", "The server was acting as a gateway or proxy and did not receive a timely request from the upstream server.")
  val HTTPVersionNotSupported       = new ServerError(505, "HTTP Version Not Supported", "The server does not support the HTTP protocol version used in the request.")
  val VariantAlsoNegotiates         = new ServerError(506, "Variant Also Negotiates", "Transparent content negotiation for the request, results in a circular reference.")
  val InsufficientStorage           = new ServerError(507, "Insufficient Storage", "Insufficient storage to complete the request.")
  val LoopDetected                  = new ServerError(508, "Loop Detected", "The server detected an infinite loop while processing the request.")
  val BandwidthLimitExceeded        = new ServerError(509, "Bandwidth Limit Exceeded", "Bandwidth limit has been exceeded.")
  val NotExtended                   = new ServerError(510, "Not Extended", "Further extensions to the request are required for the server to fulfill it.")
  val NetworkAuthenticationRequired = new ServerError(511, "Network Authentication Required", "The client needs to authenticate to gain network access.")
  val NetworkReadTimeout            = new ServerError(598, "Network read timeout error", "")
  val NetworkConnectTimeout         = new ServerError(599, "Network connect timeout error", "")
}
