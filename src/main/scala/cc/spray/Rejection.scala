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
case class MalformedQueryParamRejection(parameterName: String) extends Rejection

/**
 * Rejection created by unmarshallers.
 * Signals that the request was rejected because the requests content-type is unsupported.
 */
case class UnsupportedRequestContentTypeRejection(supported: List[ContentTypeRange]) extends Rejection

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
case class UnacceptedResponseContentTypeRejection(supported: List[ContentType]) extends Rejection

