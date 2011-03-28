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
 * The request was rejected because there was an error while unmarshalling the request content
 */
case class MalformedRequestContentRejection(message: String) extends Rejection
