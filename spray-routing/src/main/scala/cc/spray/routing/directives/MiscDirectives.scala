/*
 * Copyright (C) 2011-2012 spray.io
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

package spray.routing
package directives

import shapeless._
import akka.actor.{ActorContext, ActorSystem, ActorRefFactory}
import spray.http._
import spray.util._
import HttpHeaders._
import MediaTypes._


trait MiscDirectives {
  import BasicDirectives._

  /**
   * Returns a Directive which checks the given condition before passing on the [[spray.routing.RequestContext]] to
   * its inner Route. If the condition fails the route is rejected with a [[spray.routing.ValidationRejection]].
   */
  def validate(check: => Boolean, errorMsg: String): Directive0 =
    filter { _ => if (check) Pass.Empty else Reject(ValidationRejection(errorMsg)) }

  /**
   * Extracts an HTTP header value using the given function. If the function is undefined for all headers the request
   * is rejection with the [[spray.routing.MissingHeaderRejection]]
   */
  def headerValue[T](f: HttpHeader => Option[T]): Directive[T :: HNil] = filter {
    _.request.headers.mapFind(f) match {
      case Some(a) => Pass(a :: HNil)
      case None => Reject.Empty
    }
  }

  /**
   * Extracts an HTTP header value using the given partial function. If the function is undefined for all headers
   * the request is rejection with the [[spray.routing.MissingHeaderRejection]]
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
      case Some(wrapper) :: HNil => mapHttpResponseEntity {
        case HttpBody(ct@ ContentType(`application/json`, _), buffer) => HttpBody(
          contentType = ct.withMediaType(`application/javascript`),
          string = wrapper + '(' + buffer.asString(ct.charset.nioCharset) + ')'
        )
        case entity => entity
      }
      case _ => noop
    }
  }

  /**
   * Adds a TransformationRejection cancelling all rejections for which the given filter function returns true
   * to the list of rejections potentially coming back from the inner route.
   */
  def cancelAllRejections(cancelFilter: Rejection => Boolean): Directive0 =
    mapRejections(_ :+ TransformationRejection(_.filterNot(cancelFilter)))

  def ofType[T <: Rejection :ClassManifest]: Rejection => Boolean = {
    val erasure = classManifest[T].erasure
    erasure.isInstance(_)
  }

  def ofTypes(classes: Class[_]*): Rejection => Boolean = { rejection =>
    classes.exists(_.isInstance(rejection))
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
   * Transforms the unmatchedPath of the RequestContext using the given function.
   */
  def rewriteUnmatchedPath(f: String => String): Directive0 = mapRequestContext(_.mapUnmatchedPath(f))

  /**
   * Rejects empty requests with a RequestEntityExpectedRejection.
   * Non-empty requests are passed on unchanged to the inner route.
   */
  def rejectEmptyRequests: Directive0 = filter { ctx =>
    if (ctx.request.entity.isEmpty) Reject(RequestEntityExpectedRejection) else Pass.Empty
  }

  /**
   * Converts responses with an empty entity into (empty) rejections.
   * This way you can, for example, have the marshalling of a ''None'' option be treated as if the request could
   * not be matched.
   */
  def rejectEmptyResponse: Directive0 = mapRouteResponse {
    case HttpMessagePartWrapper(HttpResponse(_, EmptyEntity, _, _), _) => Rejected(Nil)
    case x => x
  }

  /**
   * Not a directive, but a helper function that provides access to the ActorSystem we are running in.
   */
  def actorSystem(implicit refFactory: ActorRefFactory): ActorSystem = {
    refFactory match {
      case x: ActorContext => x.system
      case x: ActorSystem => x
    }
  }
}

object MiscDirectives extends MiscDirectives