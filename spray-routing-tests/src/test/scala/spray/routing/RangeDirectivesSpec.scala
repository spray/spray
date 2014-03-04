/*
 * Copyright © 2011-2013 the spray project <http://spray.io>
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
import StatusCodes._
import spray.routing.UnsatisfiableRangeRejection
import MediaTypes.`multipart/byteranges`

class RangeDirectivesSpec extends RoutingSpec {

  def bytes(length: Byte): Route = _.complete(Array.tabulate[Byte](length)(_.toByte))

  "The `withRangeSupport` directive" should {

    val rangeCountLimit = 10
    val rangeCoalesceThreshold = 1L

    "return an Accept-Ranges(bytes) header" in {
      Get() ~> { withRangeSupport(rangeCountLimit, rangeCoalesceThreshold) { complete("any") } } ~> check {
        headers must contain(`Accept-Ranges`(BytesUnit))
      }
    }

    "return a Content-Range header for a ranged request with a single range" in {
      Get() ~> addHeader(Range(ByteRange(0, 1))) ~> {
        withRangeSupport(rangeCountLimit, rangeCoalesceThreshold) { bytes(10) }
      } ~> check {
        headers must contain(`Content-Range`(ContentRange(0, 1, Some(10))))
        status === PartialContent
        responseAs[Array[Byte]] === Array[Byte](0, 1)
      }
    }

    "return a partial response for a ranged request with a single range with undefined lastBytePosition" in {
      Get() ~> addHeader(Range(ByteRange(5))) ~> {
        withRangeSupport(rangeCountLimit, rangeCoalesceThreshold) { bytes(10) }
      } ~> check { responseAs[Array[Byte]] === Array[Byte](5, 6, 7, 8, 9) }
    }

    "return a partial response for a ranged request with a single suffix range" in {
      Get() ~> addHeader(Range(SuffixByteRange(1))) ~> {
        withRangeSupport(rangeCountLimit, rangeCoalesceThreshold) { bytes(10) }
      } ~> check { responseAs[Array[Byte]] === Array[Byte](9) }
    }

    "return a partial response for a ranged request with a overlapping suffix range" in {
      Get() ~> addHeader(Range(SuffixByteRange(100))) ~> {
        withRangeSupport(rangeCountLimit, rangeCoalesceThreshold) { bytes(10) }
      } ~> check { responseAs[Array[Byte]] === Array[Byte](0, 1, 2, 3, 4, 5, 6, 7, 8, 9) }
    }

    "reject non-GET requests" in {
      Post() ~> addHeader(Range(ByteRange(1, 2))) ~> {
        withRangeSupport(rangeCountLimit, rangeCoalesceThreshold) { bytes(5) }
      } ~> check { rejection === MethodRejection(HttpMethods.GET) }
    }

    "reject an unsatisfiable single range" in {
      Get() ~> addHeader(Range(ByteRange(100, 200))) ~> {
        withRangeSupport(rangeCountLimit, rangeCoalesceThreshold) {
          bytes(10)
        }
      } ~> check {
        rejection === UnsatisfiableRangeRejection(Seq(ByteRange(100, 200)), 10)
      }
    }

    "reject an unsatisfiable single suffixrange with suffix length 0" in {
      Get() ~> addHeader(Range(SuffixByteRange(0))) ~> {
        withRangeSupport(rangeCountLimit, rangeCoalesceThreshold) {
          bytes(42)
        }
      } ~> check {
        rejection === UnsatisfiableRangeRejection(Seq(SuffixByteRange(0)), 42)
      }
    }

    "return a mediaType of 'multipart/byteranges' for a ranged request with multiple ranges" in {
      Get() ~> addHeader(Range(ByteRange(0, 10), ByteRange(0, 10))) ~> {
        withRangeSupport(rangeCountLimit, rangeCoalesceThreshold) { bytes(10) }
      } ~> check { mediaType.withParameters(Map.empty) === `multipart/byteranges` }
    }

    "return a 'multipart/byteranges' for a ranged request with multiple coalesced ranges with preserved order" in {
      Get() ~> addHeader(Range(ByteRange(5, 10), ByteRange(0, 1), ByteRange(1, 2))) ~> {
        withRangeSupport(rangeCountLimit, rangeCoalesceThreshold) { complete("Some random and not super short entity.") }
      } ~> check {
        headers must not(haveOneElementLike { case `Content-Range`(_) ⇒ ok })
        responseAs[MultipartByteRanges] must beLike {
          case MultipartByteRanges(
            ByteRangePart(HttpEntity.NonEmpty(_, _), _ +: `Content-Range`(ContentRange(5, 10, Some(39))) +: _) +:
              ByteRangePart(HttpEntity.NonEmpty(_, _), _ +: `Content-Range`(ContentRange(0, 2, Some(39))) +: _) +:
              Seq()
            ) ⇒ ok
        }
      }
    }

    "reject a request with too many requested ranges" in {
      val ranges = (1 to 20).map(a ⇒ ByteRange(a))
      Get() ~> addHeader(Range(ranges)) ~> {
        withRangeSupport(rangeCountLimit, rangeCoalesceThreshold) { bytes(100) }
      } ~> check {
        rejection === TooManyRangesRejection(rangeCountLimit)
      }
    }

  }

}
