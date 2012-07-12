/*
 * Copyright (C) 2011-2012 spray.cc
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

package cc.spray.routing
package directives

import cc.spray.httpx.marshalling.Marshaller
import cc.spray.http._
import StatusCodes._
import akka.actor.ActorRef
import akka.dispatch.Future


trait RouteDirectives {

  /**
   * Rejects the request with the given rejections.
   */
  def reject(rejections: Rejection*)
            (implicit sender: ActorRef = null): Route = _.reject(rejections: _*)

  /**
   * Completes the request with redirection response of the given type to the given URI.
   * The default redirectionType is a temporary `302 Found`.
   */
  def redirect(uri: String, redirectionType: Redirection = Found)
              (implicit sender: SenderRef): Route = _.redirect(uri, redirectionType)

  /**
   * Completes the request with status "200 Ok" and the response entity created by marshalling the given object using
   * the in-scope marshaller for the type.
   */
  def complete[T](obj: T)
                 (implicit marshaller: Marshaller[T], sender: SenderRef): Route = complete(OK, obj)

  /**
   * Completes the request with the given status and the response entity created by marshalling the given object using
   * the in-scope marshaller for the type.
   */
  def complete[T](status: StatusCode, obj: T)
                 (implicit marshaller: Marshaller[T], sender: SenderRef): Route = complete(status, Nil, obj)

  /**
   * Completes the request with the given status, headers and the response entity created by marshalling the
   * given object using the in-scope marshaller for the type.
   */
  def complete[T](status: StatusCode, headers: List[HttpHeader], obj: T)
                 (implicit marshaller: Marshaller[T], sender: SenderRef): Route = _.complete(status, headers, obj)

  /**
   * Completes the request with the given status and the respective default message in the entity.
   */
  def complete(status: StatusCode)
              (implicit sender: SenderRef): Route = complete(status: HttpResponse)

  /**
   * Schedules the completion of the request with result of the given future.
   */
  def complete(future: Future[HttpResponse])
              (implicit sender: SenderRef): Route = _.complete(future)

  /**
   * Completes the request with the given [[cc.spray.http.HttpResponse]].
   */
  def complete(response: HttpResponse)
              (implicit sender: SenderRef): Route = _.complete(response)

  /**
   * Creates an HttpException with the given properties and bubbles it up the response chain,
   * where it is dealt with by the closest `handleExceptions` directive and its ExceptionHandler.
   */
  def fail(status: StatusCode, message: String = "")
          (implicit sender: SenderRef): Route = fail(HttpException(status, message))

  /**
   * Bubbles the given error up the response chain, where it is dealt with by the closest `handleExceptions`
   * directive and its ExceptionHandler.
   */
  def fail(error: Throwable)(implicit sender: SenderRef): Route = _.fail(error)

}

object RouteDirectives extends RouteDirectives