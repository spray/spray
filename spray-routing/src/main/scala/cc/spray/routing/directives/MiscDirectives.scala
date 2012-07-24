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

import shapeless._
import cc.spray.http._
import cc.spray.util._
import HttpHeaders._
import MediaTypes._


trait MiscDirectives {
  import BasicDirectives._

  /**
   * Transforms exceptions thrown during evaluation of its inner route using the given
   * [[cc.spray.routing.ExceptionHandler]].
   */
  def handleRejections(handler: RejectionHandler): Directive0 =
    transformRouteResponse {
      case Rejected(rejections) if handler.isDefinedAt(rejections) =>
        handler(RejectionHandler.applyTransformations(rejections))
      case x => x
    }

  /**
   * Returns a Directive which checks the given condition before passing on the [[cc.spray.routing.RequestContext]] to
   * its inner Route. If the condition fails the route is rejected with a [[cc.spray.routing.ValidationRejection]].
   */
  def validate(check: => Boolean, errorMsg: String): Directive0 =
    filter { _ => if (check) Pass.Empty else Reject(ValidationRejection(errorMsg)) }

  /**
   * Extracts an HTTP header value using the given function. If the function is undefined for all headers the request
   * is rejection with the [[cc.spray.routing.MissingHeaderRejection]]
   */
  def headerValue[T](f: HttpHeader => Option[T]): Directive[T :: HNil] = filter {
    _.request.headers.mapFind(f) match {
      case Some(a) => Pass(a :: HNil)
      case None => Reject(MissingHeaderRejection)
    }
  }

  /**
   * Extracts an HTTP header value using the given partial function. If the function is undefined for all headers
   * the request is rejection with the [[cc.spray.routing.MissingHeaderRejection]]
   */
  def headerValuePF[T](pf: PartialFunction[HttpHeader, T]): Directive[T :: HNil] = headerValue(pf.lift)

  /**
   * Directive extracting the IP of the client from either the X-Forwarded-For, Remote-Address or X-Real-IP header.
   */
  lazy val clientIP: Directive[HttpIp :: HNil] =
    (headerValuePF { case `X-Forwarded-For`(ips) => ips.head }) |
    (headerValuePF { case `Remote-Address`(ip) => ip }) |
    (headerValuePF { case RawHeader("x-real-ip", ip) => ip })

  /**
   * Wraps the inner Route with JSONP support. If a query parameter with the given name is present in the request and
   * the inner Route returns content with content-type `application/json` the response content is wrapped with a call
   * to a Javascript function having the name of query parameters value. Additionally the content-type is changed from
   * `application/json` to `application/javascript` in these cases.
   */
  def jsonpWithParameter(parameterName: String): Directive0 = {
    import ParameterDirectives._
    parameter(parameterName?).flatMap {
      case Some(wrapper) :: HNil => transformHttpResponseEntity {
        case HttpBody(ct@ ContentType(`application/json`, _), buffer) => HttpBody(
          contentType = ct.withMediaType(`application/javascript`),
          string = wrapper + '(' + new String(buffer, ct.charset.nioCharset) + ')'
        )
        case entity => entity
      }
      case _ => nop
    }
  }

  /**
   * A Directive0 that always passes the request on to its inner route
   * (i.e. does nothing with the request or the response).
   */
  def nop: Directive0 = transformInnerRoute(identityFunc)

  /**
   * Injects the given values into a directive.
   */
  def provide[L <: HList](values: L): Directive[L] = new Directive[L] {
    def happly(f: L => Route) = f(values)
  }

  /**
   * Adds a TransformRejection cancelling all rejections of the given type to the list of rejections potentially
   * coming back from its inner route.
   */
  def cancelAllRejectionsOfType[T <: Rejection :ClassManifest]: Directive0 = {
    val erasure = classManifest[T].erasure
    transformRejections(_ :+ TransformationRejection(_.filterNot(erasure.isInstance(_))))
  }

  /**
   * Rejects the request if its entity is not empty.
   */
  def requestEntityEmpty: Directive0 = filter { ctx => if (ctx.request.entity.isEmpty) Pass.Empty else Reject.Empty }

  /**
   * Rejects the request if its entity is empty.
   */
  def requestEntityPresent: Directive0 = filter { ctx => if (ctx.request.entity.isEmpty) Reject.Empty else Pass.Empty }

  /**
   * Stops the current Route processing by throwing an HttpException that will be caught by the next enclosing
   * `handleExceptions` directive and its ExceptionHandler or the enclosing Actor.
   * Failures produced in this way circumvent the usual response chain.
   * Usually you should only use this directive if you know what you are doing, the "regular" `fail` directive
   * should be appropriate for most cases.
   */
  def hardFail(status: StatusCode, message: String = "") = throw HttpException(status, message)
}

object MiscDirectives extends MiscDirectives