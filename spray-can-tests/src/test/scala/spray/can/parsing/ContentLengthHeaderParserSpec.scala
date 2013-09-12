package spray.can.parsing

import org.specs2.mutable.Specification
import java.math.BigInteger
import spray.can.parsing.SpecializedHeaderValueParsers.ContentLengthParser
import akka.util.ByteString
import spray.http.HttpHeaders.`Content-Length`

class ContentLengthHeaderParserSpec extends Specification {
  "specialized ContentLength parser" should {
    "accept zero" in {
      parse("0") === 0L
    }
    "accept positive value" in {
      parse("43234398") === 43234398L
    }
    "accept positive value > Int.MaxValue <= Long.MaxValue" in {
      parse("274877906944") === 274877906944L
      parse("9223372036854775807") === 9223372036854775807L // Long.MaxValue
    }
    "don't accept positive value > Long.MaxValue" in {
      parse("9223372036854775808") must throwA[ParsingException] // Long.MaxValue + 1
      parse("92233720368547758070") must throwA[ParsingException] // Long.MaxValue * 10 which is 0 taken overflow into account
      parse("92233720368547758080") must throwA[ParsingException] // (Long.MaxValue + 1) * 10 which is 0 taken overflow into account
    }
  }

  def parse(bigint: String): Long = {
    val (`Content-Length`(length), _) = ContentLengthParser(ByteString(bigint + "\r\n").compact, 0, _ ⇒ ())
    length
  }
}
