/*
 * Copyright © 2011-2014 the spray project <http://spray.io>
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

import spray.http._
import spray.http.HttpHeaders._
import spray.http.HttpMethods._
import spray.http.StatusCodes._

trait CacheConditionDirectives {
  import BasicDirectives._

  def conditional(eTag: EntityTag, lastModified: DateTime): Directive0 =
    mapInnerRoute { route ⇒
      ctx ⇒ {
        def ctxWithtHeaders =
          ctx.withHttpResponseMapped(_.withDefaultHeaders(List(ETag(eTag), `Last-Modified`(lastModified))))

        import ctx.request._

        def isGetOrHead = method == HEAD || method == GET
        // TODO: Cache-Control, Content-Location, Date, Expires, and Vary?
        def complete() = ctxWithtHeaders.complete(HttpResponse(NotModified))
        def fail() = ctx.complete(PreconditionFailed)

        def unmodified(ifModifiedSince: DateTime) =
          lastModified <= ifModifiedSince && ifModifiedSince.clicks < System.currentTimeMillis()

        // see http://tools.ietf.org/html/draft-ietf-httpbis-p4-conditional#section-6
        (header[`If-Match`], header[`If-Unmodified-Since`],
          header[`If-None-Match`], header[`If-Modified-Since`]) match {
            case (Some(`If-Match`(im)), _, _, _) if !im.matches(eTag, weak = false) ⇒
              fail()
            case (None, Some(`If-Unmodified-Since`(ius)), _, _) if !unmodified(ius) ⇒
              fail()
            case (_, _, Some(`If-None-Match`(inm)), _) if inm.matches(eTag, weak = true) ⇒
              if (isGetOrHead) complete() else fail()
            case (_, _, None, Some(`If-Modified-Since`(ims))) if isGetOrHead && unmodified(ims) ⇒
              complete()
            // TODO: Range/If-Range
            case _ ⇒
              route(ctxWithtHeaders)
          }
      }
    }
}

object CacheConditionDirectives extends CacheConditionDirectives
