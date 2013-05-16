package spray.http

import org.specs2.mutable.Specification
import java.io.{ ByteArrayOutputStream, ObjectOutputStream }
import spray.http.HttpCharsets.CustomHttpCharset

class HttpModelSerializabilitySpec extends Specification {
  "HttpRequests" should {
    "be serializable" in {
      "empty" in { HttpRequest() must beSerializable }
      "with complex URI" in {
        HttpRequest(uri = Uri("/test?blub=28&x=5+3")) must beSerializable
      }
      "with content type" in {
        HttpRequest().withEntity(HttpEntity(ContentType.`application/json`, Array.empty[Byte])) must beSerializable
      }
      "with accepted media types" in {
        HttpRequest().withHeaders(HttpHeaders.Accept(MediaTypes.`application/json`)) must beSerializable
      }
      "with accept-charset" in {
        HttpRequest().withHeaders(HttpHeaders.`Accept-Charset`(HttpCharsets.`UTF-16`)) must beSerializable
        HttpRequest().withHeaders(HttpHeaders.`Accept-Charset`(CustomHttpCharset("utf8").get)) must beSerializable
      }
      "with accepted encodings" in {
        HttpRequest().withHeaders(HttpHeaders.`Accept-Encoding`(HttpEncodings.chunked)) must beSerializable
        HttpRequest().withHeaders(HttpHeaders.`Accept-Encoding`(HttpEncodings.CustomHttpEncoding("test"))) must beSerializable
      }
    }
  }
  "HttpResponse" should {
    "be serializable" in {
      "empty" in { HttpResponse() must beSerializable }
    }
  }
  "Header values" should {
    "be serializable" in {
      "Content-Disposition" in { ContentDispositions.`form-data` must beSerializable }
      "Cache" in { CacheDirectives.`no-store` must beSerializable }
      "DateTime" in { DateTime.now must beSerializable }
    }
  }

  def beSerializable =
    (throwA[java.io.NotSerializableException] ^^ (tryToSerialize _)).not

  def tryToSerialize(obj: AnyRef) = {
    val oos = new ObjectOutputStream(new ByteArrayOutputStream)
    try oos.writeObject(obj)
    finally oos.close()
  }
}
