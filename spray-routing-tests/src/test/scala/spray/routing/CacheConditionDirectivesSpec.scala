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

import spray.http._
import spray.http.StatusCodes._
import spray.http.HttpHeaders._

class CacheConditionDirectivesSpec extends RoutingSpec {

  "the conditional directive" should {
    val timestamp = DateTime.now - 2000L
    val ifUnmodifiedSince = `If-Unmodified-Since`(timestamp)
    val ifModifiedSince = `If-Modified-Since`(timestamp)
    val tag = EntityTag("fresh")
    val ifMatch = `If-Match`(tag)
    val ifNoneMatch = `If-None-Match`(tag)
    val responseHeaders = List(ETag(tag), `Last-Modified`(timestamp))

    def taggedAndTimestamped = conditional(tag, timestamp) { completeOk }
    def weak = conditional(tag.copy(weak = true), timestamp) { completeOk }

    "return OK for new resources" in {
      Get() ~> taggedAndTimestamped ~> check {
        status === OK
        headers must containAllOf(responseHeaders)
      }
    }

    "return OK for non-matching resources" in {
      Get() ~> addHeader(`If-None-Match`(EntityTag("old"))) ~> taggedAndTimestamped ~> check {
        status === OK
        headers must containAllOf(responseHeaders)
      }
      Get() ~> addHeader(`If-Modified-Since`(timestamp - 1000L)) ~> taggedAndTimestamped ~> check {
        status === OK
        headers must containAllOf(responseHeaders)
      }
      Get() ~> addHeaders(`If-None-Match`(EntityTag("old")), `If-Modified-Since`(timestamp - 1000L)) ~> taggedAndTimestamped ~> check {
        status === OK
        headers must containAllOf(responseHeaders)
      }
    }

    "ignore If-Modified-Since if If-None-Match is defined" in {
      Get() ~> addHeaders(ifNoneMatch, `If-Modified-Since`(timestamp - 1000L)) ~> taggedAndTimestamped ~> check {
        status === NotModified
      }
      Get() ~> addHeaders(`If-None-Match`(EntityTag("old")), ifModifiedSince) ~> taggedAndTimestamped ~> check {
        status === OK
      }
    }

    "return PreconditionFailed for matched but unsafe resources" in {
      Put() ~> addHeaders(ifNoneMatch, ifModifiedSince) ~> taggedAndTimestamped ~> check {
        status === PreconditionFailed
        headers === Nil
      }
    }

    "return NotModified for matching resources" in {
      Get() ~> addHeaders(`If-None-Match`.`*`, ifModifiedSince) ~> taggedAndTimestamped ~> check {
        status === NotModified
        headers must containAllOf(responseHeaders)
      }
      Get() ~> addHeaders(ifNoneMatch, ifModifiedSince) ~> taggedAndTimestamped ~> check {
        status === NotModified
        headers must containAllOf(responseHeaders)
      }
      Get() ~> addHeaders(ifNoneMatch, `If-Modified-Since`(timestamp + 1000L)) ~> taggedAndTimestamped ~> check {
        status === NotModified
        headers must containAllOf(responseHeaders)
      }
      Get() ~> addHeaders(`If-None-Match`(tag.copy(weak = true)), ifModifiedSince) ~> taggedAndTimestamped ~> check {
        status === NotModified
        headers must containAllOf(responseHeaders)
      }
      val multi = `If-None-Match`(tag, EntityTag("some"), EntityTag("other"))
      Get() ~> addHeaders(multi, ifModifiedSince) ~> taggedAndTimestamped ~> check {
        status === NotModified
        headers must containAllOf(responseHeaders)
      }
    }

    "return NotModified when only one matching header is set" in {
      Get() ~> addHeader(`If-None-Match`.`*`) ~> taggedAndTimestamped ~> check {
        status === NotModified
        headers must containAllOf(responseHeaders)
      }
      Get() ~> addHeader(ifNoneMatch) ~> taggedAndTimestamped ~> check {
        status === NotModified
        headers must containAllOf(responseHeaders)
      }
      Get() ~> addHeader(ifModifiedSince) ~> taggedAndTimestamped ~> check {
        status === NotModified
        headers must containAllOf(responseHeaders)
      }
    }

    "return NotModified for matching weak resources" in {
      val weakTag = tag.copy(weak = true)
      Get() ~> addHeader(ifNoneMatch) ~> weak ~> check {
        status === NotModified
        headers must containAllOf(List(ETag(weakTag), `Last-Modified`(timestamp)))
      }
      Get() ~> addHeader(`If-None-Match`(weakTag)) ~> weak ~> check {
        status === NotModified
        headers must containAllOf(List(ETag(weakTag), `Last-Modified`(timestamp)))
      }
    }

    "return normally for matching If-Match/If-Unmodified" in {
      Put() ~> addHeader(`If-Match`.`*`) ~> taggedAndTimestamped ~> check {
        status === OK
        headers must containAllOf(responseHeaders)
      }
      Put() ~> addHeader(ifMatch) ~> taggedAndTimestamped ~> check {
        status === OK
        headers must containAllOf(responseHeaders)
      }
      Put() ~> addHeader(ifUnmodifiedSince) ~> taggedAndTimestamped ~> check {
        status === OK
        headers must containAllOf(responseHeaders)
      }
    }

    "return PreconditionFailed for non-matching If-Match/If-Unmodified" in {
      Put() ~> addHeader(`If-Match`(EntityTag("old"))) ~> taggedAndTimestamped ~> check {
        status === PreconditionFailed
        headers === Nil
      }
      Put() ~> addHeader(`If-Unmodified-Since`(timestamp - 1000L)) ~> taggedAndTimestamped ~> check {
        status === PreconditionFailed
        headers === Nil
      }
    }

    "ignore If-Unmodified-Since if If-Match is defined" in {
      Put() ~> addHeaders(ifMatch, `If-Unmodified-Since`(timestamp - 1000L)) ~> taggedAndTimestamped ~> check {
        status === OK
      }
      Put() ~> addHeaders(`If-Match`(EntityTag("old")), ifModifiedSince) ~> taggedAndTimestamped ~> check {
        status === PreconditionFailed
      }
    }
  }

}
