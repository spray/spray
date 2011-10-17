/*
 * Copyright (C) 2011 Mathias Doenitz
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

package cc.spray

import http._

/**
 * A rejection encapsulates a specific reason why a Route was not able to handle a request. Rejections are gathered
 * up over the course of a Route evaluation and finally converted to [[cc.spray.http.HttpResponse]]s by the
 *  [[cc.spray.HttpService]] actor, if there was no way for the request to be completed.
 */
trait Rejection

/**
 * Rejection created by method filters.
 * Signals that the request was rejected because the HTTP method is unsupported.
 */
case class MethodRejection(supported: HttpMethod) extends Rejection

/**
 * Rejection created by parameter filters.
 * Signals that the request was rejected because a query parameter was not found.
 */
case class MissingQueryParamRejection(parameterName: String) extends Rejection

/**
 * Rejection created by parameter filters.
 * Signals that the request was rejected because a query parameter could not be interpreted.
 */
case class MalformedQueryParamRejection(errorMsg: String, parameterName: String) extends Rejection

/**
 * Rejection created by form field filters.
 * Signals that the request was rejected because a form field was not found.
 */
case class MissingFormFieldRejection(fieldName: String) extends Rejection

/**
 * Rejection created by form field filters.
 * Signals that the request was rejected because a form field could not be interpreted.
 */
case class MalformedFormFieldRejection(errorMsg: String, fieldName: String) extends Rejection

/**
 * Rejection created by unmarshallers.
 * Signals that the request was rejected because the requests content-type is unsupported.
 */
case class UnsupportedRequestContentTypeRejection(errorMsg: String) extends Rejection

/**
 * Rejection created by decoding filters.
 * Signals that the request was rejected because the requests content encoding is unsupported.
 */
case class UnsupportedRequestEncodingRejection(supported: HttpEncoding) extends Rejection

/**
 * Rejection created by decoding filters.
 * Signals that the request was rejected because the requests content is corrupted.
 */
case class CorruptRequestEncodingRejection(msg: String) extends Rejection

/**
 * Rejection created by unmarshallers.
 * Signals that the request was rejected because there was an error while unmarshalling the request content
 */
case class MalformedRequestContentRejection(message: String) extends Rejection

/**
 * Rejection created by unmarshallers.
 * Signals that the request was rejected because an message body entity was expected but not supplied. 
 */
case object RequestEntityExpectedRejection extends Rejection

/**
 * Rejection created by marshallers.
 * Signals that the request was rejected because the service is not capable of producing a response entity whose
 * content type is accepted by the client
 */
case class UnacceptedResponseContentTypeRejection(supported: Seq[ContentType]) extends Rejection

/**
 * Rejection created by encoding filters.
 * Signals that the request was rejected because the service is not capable of producing a response entity whose
 * content encoding is accepted by the client
 */
case class UnacceptedResponseEncodingRejection(supported: HttpEncoding) extends Rejection

/**
 * Special Rejection that is used to cancel Rejections that have been gathered up but are "obsolete" since
 * another Route of the same type has matched the request.
 */
case class RejectionRejection(reject: Rejection => Boolean) extends Rejection

/**
 * Rejection created by the 'authenticate' directive.
 * Signals that the request was rejected because the user could not be authenticated.
 */
case class AuthenticationFailedRejection(realm: String) extends Rejection

/**
 * Rejection created by the 'authorize' directive.
 * Signals that the request was rejected because the user is not authorized.
 */
case object AuthorizationFailedRejection extends Rejection

/**
 * Rejection created by the 'authenticate' directive.
 * Signals that the request was rejected because no authorization was supplied 
 */
case class AuthenticationRequiredRejection(scheme: String, realm: String, params: Map[String, String] = Map.empty)
        extends Rejection

/**
 * Rejection created by the `validation` directive.
 */
case class ValidationRejection(message: String) extends Rejection

object Rejections {
  def applyCancellations(rejections: Set[Rejection]): Set[Rejection] = {
    val cancellations = rejections.collect { case RejectionRejection(f) => f }
    rejections.filterNot(r => cancellations.exists(_(r)) || r.isInstanceOf[RejectionRejection])
  }
}