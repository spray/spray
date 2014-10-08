package docs.directives

import spray.http._
import HttpHeaders._

class RangeDirectivesExamplesSpec extends DirectivesSpec {

  "withRangeSupport" in {
    val route =
      withRangeSupport(4, 2L) {
        complete("ABCDEFGH")
      }

    Get() ~> addHeader(Range(ByteRange(3, 4))) ~> route ~> check {
      headers must contain(`Content-Range`(ContentRange(3, 4, 8)))
      status === StatusCodes.PartialContent
      responseAs[String] === "DE"
    }

    Get() ~> addHeader(Range(ByteRange(0, 1), ByteRange(1, 2), ByteRange(6, 7))) ~> route ~> check {
      headers must not(contain(like[HttpHeader] { case `Content-Range`(_, _) ⇒ ok }))
      responseAs[MultipartByteRanges] must beLike {
        case MultipartByteRanges(
          BodyPart(entity1, `Content-Range`(RangeUnit.Bytes, range1) +: _) +:
          BodyPart(entity2, `Content-Range`(RangeUnit.Bytes, range2) +: _) +: Seq()
        ) ⇒ entity1.asString === "ABC" and range1 === ContentRange(0, 2, 8) and
          entity2.asString === "GH" and range2 === ContentRange(6, 7, 8)
      }
    }
  }
}
