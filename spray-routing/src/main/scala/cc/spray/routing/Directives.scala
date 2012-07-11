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

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import shapeless._
import cc.spray.httpx.marshalling.Marshaller
import cc.spray.util._
import cc.spray.http._
import HttpMethods._
import StatusCodes._
import HttpHeaders._
import MediaTypes._


trait Directives extends RouteConcatenation {
  def system: ActorSystem
  def log: LoggingAdapter

  def handleExceptions(implicit handler: ExceptionHandler) = Directive.wrapping { inner => ctx =>
    try inner(ctx)
    catch handler andThen (_(log)(ctx))
  }

  def handleRejections(implicit handler: RejectionHandler) = Directive.routeResponseTransforming {
    case Rejected(rejections) => handler(rejections)
    case x => x
  }

  /**
   * A route filter that rejects all non-DELETE requests.
   */
  val delete = method(DELETE)

  /**
   * A route filter that rejects all non-GET requests.
   */
  val get = method(GET)

  /**
   * A route filter that rejects all non-PATCH requests.
   */
  val patch = method(PATCH)

  /**
   * A route filter that rejects all non-POST requests.
   */
  val post = method(POST)

  /**
   * A route filter that rejects all non-PUT requests.
   */
  val put = method(PUT)


  /**
   * Returns a route filter that rejects all requests whose HTTP method does not match the given one.
   */
  def method(m: HttpMethod): Directive0 = Directive.filtering { ctx =>
    if (ctx.request.method == m) Pass.Empty else Reject(MethodRejection(m))
  }

  /**
   * Completes the request with the given [[cc.spray.http.HttpResponse]].
   */
  def completeWith(response: => HttpResponse): Route = _.complete(response)

  /**
   * Completes the request with status "200 Ok" and the response content created by marshalling the given object using
   * the in-scope marshaller for the type.
   */
  def completeWith[T :Marshaller](value: => T): Route = _.complete(value)

  /**
   * Completes the request with redirection response of the given type to the given URI.
   * The default redirectionType is a temporary `302 Found`.
   */
  def redirect(uri: String, redirectionType: Redirection = StatusCodes.Found): Route = _.redirect(uri, redirectionType)

  /**
   * Rejects the request with the given rejections.
   */
  def reject(rejections: Rejection*): Route = _.reject(rejections: _*)

  /**
   * Returns a Directive which checks the given condition before passing on the [[cc.spray.routing.RequestContext]] to
   * its inner Route. If the condition fails the route is rejected with a [[cc.spray.routing.ValidationRejection]].
   */
  def validate(check: => Boolean, errorMsg: String) = Directive.filtering { _ =>
    if (check) Pass.Empty else Reject(ValidationRejection(errorMsg))
  }

  /**
   * Returns a Route that sets the given response status on all not-rejected responses of its inner Route.
   */
  def respondWithStatus(responseStatus: StatusCode) = Directive.httpResponseTransforming {
    _.copy(status = responseStatus)
  }

  /**
   * Returns a Route that adds the given response header to all not-rejected responses of its inner Route.
   */
  def respondWithHeader(responseHeader: HttpHeader) = Directive.httpResponseTransforming { response =>
    response.copy(headers = responseHeader :: response.headers)
  }

  /**
   * Returns a Route that adds the given response headers to all not-rejected responses of its inner Route.
   */
  def respondWithHeaders(responseHeaders: HttpHeader*) = {
    val headers = responseHeaders.toList
    Directive.httpResponseTransforming { response =>
      response.copy(headers = headers ::: response.headers)
    }
  }

  /**
   * Returns a Route that sets the content-type of non-empty, non-rejected responses of its inner Route to the given
   * ContentType.
   */
  def respondWithContentType(contentType: ContentType) = Directive.httpResponseTransforming { response =>
    response.copy(entity = response.entity.map((ct, buffer) => (contentType, buffer)))
  }

  /**
   * Returns a Route that sets the media-type of non-empty, non-rejected responses of its inner Route to the given
   * one.
   */
  def respondWithMediaType(mediaType: MediaType) = Directive.httpResponseTransforming { response =>
    response.copy(entity = response.entity.map((ct, buffer) => (ct.withMediaType(mediaType), buffer)))
  }

  /**
   * Extracts an HTTP header value using the given function. If the function is undefined for all headers the request
   * is rejection with the [[cc.spray.routing.MissingHeaderRejection]]
   */
  def headerValue[T](f: HttpHeader => Option[T]) = Directive.filtering {
    _.request.headers.mapFind(f) match {
      case Some(a) => Pass(a :: HNil)
      case None => Reject(MissingHeaderRejection)
    }
  }

  /**
   * Extracts an HTTP header value using the given partial function. If the function is undefined for all headers
   * the request is rejection with the [[cc.spray.routing.MissingHeaderRejection]]
   */
  def headerValuePF[T](pf: PartialFunction[HttpHeader, T]) = headerValue(pf.lift)

  /**
   * Directive extracting the IP of the client from either the X-Forwarded-For, Remote-Address or X-Real-IP header.
   */
  lazy val clientIP: Directive[HttpIp :: HNil] =
    headerValuePF { case `X-Forwarded-For`(ips) => ips.head } |
    headerValuePF { case `Remote-Address`(ip) => ip } |
    headerValuePF { case RawHeader("x-real-ip", ip) => ip }

  /**
   * Wraps the inner Route with JSONP support. If a query parameter with the given name is present in the request and
   * the inner Route returns content with content-type `application/json` the response content is wrapped with a call
   * to a Javascript function having the name of query parameters value. Additionally the content-type is changed from
   * `application/json` to `application/javascript` in these cases.
   */
  def jsonpWithParameter(parameterName: String) = Directive.requestContextTransforming { ctx =>
    ctx.withHttpResponseTransformed {
      _.withEntityTransformed {
        case body@ HttpBody(ct@ ContentType(`application/json`, _), buffer) =>
          ctx.request.queryParams.get(parameterName) match {
            case Some(wrapper) => HttpBody(
              ct.withMediaType(`application/javascript`),
              wrapper + '(' + new String(buffer, ct.charset.nioCharset) + ')'
            )
            case None => body
          }
        case entity => entity
      }
    }
  }

  /**
   * Stops the current Route processing by throwing an HttpException that will be caught by the enclosing Actor.
   * Failures produced in this way circumvent all response processing logic that might be present (for example they
   * cannot be cached with the 'cache' directive).
   */
  def hardFail(failure: HttpFailure, reason: String = ""): Nothing = throw new HttpException(failure, reason)
}
