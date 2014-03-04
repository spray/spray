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

import spray.http.HttpHeaders.{ Range, `Accept-Ranges`, `Content-Range` }
import spray.http._
import shapeless.HNil
import spray.routing.directives.RouteDirectives._
import spray.http.ByteRangePart
import spray.http.ContentRange
import spray.routing.TooManyRangesRejection
import spray.http.SuffixByteRange
import spray.routing.RequestContext
import spray.http.HttpResponse
import spray.routing.UnsatisfiableRangeRejection

trait RangeDirectives {
  import BasicDirectives._
  import MethodDirectives._
  import RespondWithDirectives.respondWithHeader
  import StatusCodes.PartialContent

  /**
   * Answers GET requests with a `Accept-Ranges: bytes` header and converts HttpResponses coming back from its inner route
   * into partial responses if the initial request contained a valid `Range` request header. The requested byte-ranges may
   * be coalesced.
   * Rejects non-GET requests with a `MethodRejection`.
   * Rejects requests with no satisfiable ranges `UnsatisfiableRangeRejection`.
   * Rejects requests with too many expected ranges.
   * @see rfc2616 14.35.2
   */
  def withRangeSupport()(implicit settings: RoutingSettings): Directive0 = withRangeSupport(settings.rangeCountLimit, settings.rangeCoalesceThreshold)

  /**
   * Answers GET requests with a `Accept-Ranges: bytes` header and converts HttpResponses coming back from its inner route
   * into partial responses if the initial request contained a valid `Range` request header. The requested byte-ranges may
   * be coalesced.
   * Rejects non-GET requests with a `MethodRejection`.
   * Rejects requests with no satisfiable ranges `UnsatisfiableRangeRejection`.
   * Rejects requests with too many expected ranges.
   * @see rfc2616 14.35.2
   */
  def withRangeSupport(rangeCountLimit: Int, rangeCoalesceThreshold: Long): Directive0 = get & respondWithAcceptByteRangesHeader & applyRanges(rangeCountLimit, rangeCoalesceThreshold)

  private val respondWithAcceptByteRangesHeader: Directive0 = respondWithHeader(`Accept-Ranges`(BytesUnit))

  private def applyRanges(rangeCountLimit: Int, rangeCoalesceThreshold: Long): Directive0 = {
    extract(_.request.header[Range]).flatMap[HNil] {
      case None ⇒ pass
      case Some(Range(requestedRanges)) if requestedRanges.size > rangeCountLimit ⇒ reject(TooManyRangesRejection(rangeCountLimit))
      case Some(Range(requestedRanges)) ⇒ applyMultipleRanges(rangeCoalesceThreshold, requestedRanges)
    }
  }

  private def applyMultipleRanges(rangeCoalesceThreshold: Long, requestedRanges: Seq[ByteRangeSetEntry]): Directive0 = {
    mapRequestContext { ctx ⇒
      ctx.withRouteResponseHandling {
        case HttpResponse(status, responseEntity @ HttpEntity.NonEmpty(contentType, data), responseHeaders, _) ⇒ {
          val entityLength = data.length
          val satisfiableRanges = requestedRanges.filter(satisfiableRange(entityLength))
          if (satisfiableRanges.isEmpty) {
            ctx.reject(UnsatisfiableRangeRejection(requestedRanges, entityLength))
          } else if (requestedRanges.size == 1) {
            completeWithSingleByteRange(ctx, requestedRanges(0), responseEntity, responseHeaders)
          } else {
            completeWithMultipartByteRanges(ctx, rangeCoalesceThreshold, satisfiableRanges, responseEntity, responseHeaders)
          }
        }
      }

    }
  }

  private def completeWithMultipartByteRanges(ctx: RequestContext, rangeCoalesceThreshold: Long, satisfiableRanges: Seq[ByteRangeSetEntry],
                                              responseEntity: HttpEntity.NonEmpty, responseHeaders: List[HttpHeader]): Unit = {
    val responseEntityLength = responseEntity.data.length
    val appliedRanges = satisfiableRanges.map(applyRange(responseEntityLength))
    val coalescedRanges = coalesceRanges(rangeCoalesceThreshold)(appliedRanges)
    val bodyParts = coalescedRanges.map(r ⇒
      ByteRangePart(HttpEntity(responseEntity.contentType, responseEntity.data.slice(r.firstBytePosition, r.length)),
        Seq(`Content-Range`(ContentRange(r.firstBytePosition, r.lastBytePosition, Some(responseEntityLength))))))

    ctx.complete(PartialContent, responseHeaders, MultipartByteRanges(bodyParts))

  }

  private def completeWithSingleByteRange(ctx: RequestContext, satisfiableRange: ByteRangeSetEntry, responseEntity: HttpEntity.NonEmpty, responseHeaders: List[HttpHeader]): Unit = {
    val responseEntityLength = responseEntity.data.length
    val appliedRange = applyRange(responseEntityLength)(satisfiableRange)
    val contentRangeHeader: HttpHeader = `Content-Range`(ContentRange(appliedRange.firstBytePosition, appliedRange.lastBytePosition, Some(responseEntityLength)))
    val partialData = responseEntity.data.slice(appliedRange.firstBytePosition, appliedRange.length)
    val partialEntity = HttpEntity(responseEntity.contentType, partialData)
    val partialResponse = HttpResponse(status = PartialContent, headers = contentRangeHeader :: responseHeaders, entity = partialEntity)
    ctx.complete(partialResponse)
  }

  private case class AppliedByteRange(firstBytePosition: Long, lastBytePosition: Long) {
    val length: Long = lastBytePosition - firstBytePosition + 1

    def shortestDistanceTo(other: AppliedByteRange): Long = {
      math.max(0,
        if (firstBytePosition <= other.firstBytePosition) {
          other.firstBytePosition - lastBytePosition
        } else {
          firstBytePosition - other.lastBytePosition
        })
    }
    def mergeWith(other: AppliedByteRange): AppliedByteRange =
      AppliedByteRange(math.min(firstBytePosition, other.firstBytePosition), math.max(lastBytePosition, other.lastBytePosition))
  }

  private def applyRange(entityLength: Long)(range: ByteRangeSetEntry): AppliedByteRange = {
    range match {
      case ByteRange(from, None)         ⇒ AppliedByteRange(from, entityLength - 1L)
      case ByteRange(from, Some(to))     ⇒ AppliedByteRange(from, math.min(to, entityLength - 1L))
      case SuffixByteRange(suffixLength) ⇒ AppliedByteRange(math.max(0, entityLength - suffixLength), entityLength - 1L)
    }
  }

  /**
   * When multiple ranges are requested, a server may coalesce any of the ranges that overlap or that are separated
   * by a gap that is smaller than the overhead of sending multiple parts, regardless of the order in which the
   * corresponding byte-range-spec appeared in the received Range header field. Since the typical overhead between
   * parts of a multipart/byteranges payload is around 80 bytes, depending on the selected representation's
   * media type and the chosen boundary parameter length, it can be less efficient to transfer many small
   * disjoint parts than it is to transfer the entire selected representation.
   */
  private def coalesceRanges(threshold: Long)(ranges: Seq[AppliedByteRange]): Seq[AppliedByteRange] = {

    ranges.foldLeft(Seq.empty[AppliedByteRange]) { (akku, next) ⇒
      val (mergeCandidates, otherCandidates) = akku.partition(_.shortestDistanceTo(next) <= threshold)
      val merged = mergeCandidates.foldLeft(next)((a, b) ⇒ a.mergeWith(b))
      otherCandidates :+ merged
    }
  }

  /**
   * If a valid byte-range-set includes at least one byte-range-spec with a first-byte-pos that is
   * less than the current length of the representation, or at least one suffix-byte-range-spec
   * with a non-zero suffix-length, then the byte-range-set is satisfiable.
   */
  private def satisfiableRange(entityLength: Long)(range: ByteRangeSetEntry) = range match {
    case ByteRange(firstBytePosition, _) if firstBytePosition >= entityLength ⇒ false
    case SuffixByteRange(0) ⇒ false
    case _ ⇒ true
  }

}

object RangeDirectives extends RangeDirectives

