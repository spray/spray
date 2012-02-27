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
package directives

import http._
import StatusCodes.Redirection
import HttpHeaders._

private[spray] trait MiscDirectives {
  this: BasicDirectives with ParameterDirectives =>

  /**
   * Completes the request with the given [[cc.spray.http.HttpResponse]].
   */
  def completeWith(response: => HttpResponse): Route =
    _.complete(response)

  /**
   * Completes the request with redirection response of the given type to the given URI.
   * The default redirectionType is a temporary `302 Found`.
   */
  def redirect(uri: String, redirectionType: Redirection = StatusCodes.Found): Route =
    _.redirect(uri, redirectionType)

  /**
   * Rejects the request with the given rejections.
   */
  def reject(rejections: Rejection*): Route =
    _.reject(rejections: _*)

  /**
   * Returns a Route which checks the given condition before passing on the [[cc.spray.RequestContext]] to its inner
   * Route. If the condition failes the route is rejected with a [[cc.spray.ValidationRejection]].
   */
  def validate(check: => Boolean, errorMsg: String) = filter { _ =>
    if (check) Pass else Reject(ValidationRejection(errorMsg))
  }

  /**
   * Returns a Route which applies the given [[cc.spray.http.HttpRequest]] transformation function before passing on the
   *  [[cc.spray.RequestContext]] to its inner Route.
   */
  def transformRequest(f: HttpRequest => HttpRequest) =
    transformRequestContext(_.withRequestTransformed(f))

  /**
   * Returns a Route which applies the given [[cc.spray.http.HttpResponse]] transformation function to all not-rejected
   * responses (regular or chunked) of its inner Route.
   */
  def transformResponse(f: HttpResponse => HttpResponse) =
    transformRequestContext(_.withResponseTransformed(f))

  /**
   * Returns a Route which applies the given [[cc.spray.Rejection]] transformation function to all rejected responses
   * of its inner Route.
   */
  def transformRejections(f: Set[Rejection] => Set[Rejection]) =
    transformRequestContext(_.withRejectionsTransformed(f))

  /**
   * Returns a Route which applies the given [[cc.spray.http.HttpResponse]] transformation function to all not-rejected
   * "regular" responses of its inner Route.
   */
  def transformUnchunkedResponse(f: HttpResponse => HttpResponse) =
    transformRequestContext(_.withUnchunkedResponseTransformed(f))

  /**
   * Returns a Route which applies the given [[cc.spray.http.HttpResponse]] transformation function to all not-rejected
   * chunked responses of its inner Route.
   */
  def transformChunkedResponse(f: HttpResponse => HttpResponse) =
    transformRequestContext(_.withChunkedResponseTransformed(f))

  /**
   * Returns a Route that sets the given response status on all not-rejected responses of its inner Route.
   */
  def respondWithStatus(responseStatus: StatusCode) = transformResponse { response =>
    response.copy(status = responseStatus)
  }

  /**
   * Returns a Route that adds the given response header to all not-rejected responses of its inner Route.
   */
  def respondWithHeader(responseHeader: HttpHeader) = transformResponse { response =>
    response.copy(headers = responseHeader :: response.headers)
  }
  
  /**
   * Returns a Route that adds the given response headers to all not-rejected responses of its inner Route.
   */
  def respondWithHeaders(responseHeaders: HttpHeader*) = {
    val headers = responseHeaders.toList 
    transformResponse { response => response.copy(headers = headers ::: response.headers) }
  }

  /**
   * Returns a Route that sets the content-type of non-empty, non-rejected responses of its inner Route to the given
   * ContentType.
   */
  def respondWithContentType(contentType: ContentType) = transformResponse { response =>
    response.copy(content = response.content.map(_.withContentType(contentType)))
  }
  
  /**
   * Returns a Route that sets the media-type of non-empty, non-rejected responses of its inner Route to the given
   * one.
   */
  def respondWithMediaType(mediaType: MediaType) = transformResponse { response =>
    response.copy(content = response.content.map(c => c.withContentType(ContentType(mediaType, c.contentType.charset))))
  }

  /**
   * Extracts an HTTP header value using the given function.
   * If the function is undefined for all headers the request is rejection with the [[cc.spray.MissingHeaderRejection]]
   */
  def headerValue[A](f: HttpHeader => Option[A]) = filter1 {
    import utils._
    _.request.headers.mapFind(f) match {
      case Some(a) => Pass(a)
      case None => Reject(MissingHeaderRejection)
    }
  }

  /**
   * Extracts an HTTP header value using the given function.
   * If the function is undefined for all headers the request is rejection with the [[cc.spray.MissingHeaderRejection]]
   */
  def headerValuePF[A](pf: PartialFunction[HttpHeader, A]) =
    headerValue(pf.lift)

  /**
   * Directive extracting the IP of the client from the X-Forwarded-For or X-Real-IP header (if present) or from the
   * sender IP of the HTTP request.
   */
  lazy val clientIP: SprayRoute1[String] = {
    headerValuePF {
      case x: `X-Forwarded-For` => x.ips(0).value
    } | headerValuePF {
      case CustomHeader("x-real-ip", ip) => ip
    } | filter1 { ctx =>
      Pass(ctx.remoteHost.value)
    }
  }

  /**
   * Wraps the inner Route with JSONP support. If a query parameter with the given name is present in the request and
   * the inner Route returns content with content-type `application/json` the response content is wrapped with a call
   * to a Javascript function having the name of query parameters value. Additionally the content-type is changed from
   * `application/json` to `application/javascript` in these cases.
   */
  def jsonpWithParameter(parameterName: String) = transformRequestContext { ctx =>
    ctx.withResponseTransformed {
      _.withContentTransformed { content =>
        import MediaTypes._
        import typeconversion.DefaultUnmarshallers._
        (ctx.request.queryParams.get(parameterName), content.contentType) match {
          case (Some(wrapper), ContentType(`application/json`, charset)) =>
            HttpContent(ContentType(`application/javascript`, charset), wrapper + '(' + content.as[String].right.get + ')')
          case _ => content
        }
      }
    }
  }
  
  /**
   * Stops the current Route processing by throwing an HttpException that will be caught by the enclosing Actor.
   * Failures produced in this way circumvent all response processing logic that might be present (for example they
   * cannot be cached with the 'cache' directive).
   */
  def hardFail(failure: HttpFailure, reason: String = ""): Nothing = throw new HttpException(failure, reason)
  
  implicit def pimpRouteWithConcatenation(route: Route) = new RouteConcatenation(route: Route)
  
  class RouteConcatenation(route: Route) {
    /**
     * Returns a Route that chains two Routes. If the first Route rejects the request the second route is given a
     * chance to act upon the request.
     */
    def ~ (other: Route): Route = { ctx =>
      route {
        ctx.withReject { rejections1 =>
          other {
            ctx.withReject(rejections2 => ctx.reject(rejections1 ++ rejections2))
          }
        }
      }
    }
  }
  
}