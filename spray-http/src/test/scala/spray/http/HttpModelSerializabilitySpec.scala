/*
 * Copyright (C) 2011-2013 spray.io
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

package spray.http

import java.io._
import org.specs2.mutable.Specification
import HttpHeaders._

class HttpModelSerializabilitySpec extends Specification {

  "HttpRequests" should {
    "be serializable" in {
      "empty" in { HttpRequest() must beSerializable }
      "with complex URI" in {
        HttpRequest(uri = Uri("/test?blub=28&x=5+3")) must beSerializable
      }
      "with content type" in {
        HttpRequest().withEntity(HttpEntity(ContentTypes.`application/json`, HttpData.Empty)) must beSerializable
      }
      "with accepted media types" in {
        HttpRequest().withHeaders(Accept(MediaTypes.`application/json`)) must beSerializable
      }
      "with accept-charset" in {
        HttpRequest().withHeaders(`Accept-Charset`(HttpCharsets.`UTF-16`)) must beSerializable
        HttpRequest().withHeaders(`Accept-Charset`(HttpCharset.custom("utf8").get)) must beSerializable
      }
      "with accepted encodings" in {
        HttpRequest().withHeaders(`Accept-Encoding`(HttpEncodings.chunked)) must beSerializable
        HttpRequest().withHeaders(`Accept-Encoding`(HttpEncoding.custom("test"))) must beSerializable
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
      "Cache" in { CacheDirectives.`no-store` must beSerializable }
      "DateTime" in { DateTime.now must beSerializable }
      "Charsets" in {
        tryToSerialize(HttpCharsets.`UTF-16`).nioCharset === HttpCharsets.`UTF-16`.nioCharset
      }
      "LanguageRange" in {
        Language("a", "b") must beSerializable
        LanguageRanges.`*` must beSerializable
      }
      "MediaRange" in { MediaRanges.`application/*` must beSerializable }
    }
  }

  def beSerializable =
    (throwA[java.lang.Throwable] ^^ (tryToSerialize[AnyRef] _)).not

  def tryToSerialize[T](obj: T): T = {
    val baos = new ByteArrayOutputStream
    val oos = new ObjectOutputStream(baos)
    oos.writeObject(obj)
    oos.close()
    // make sure to use correct class loader
    val loader = classOf[HttpRequest].getClassLoader
    val ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray)) {
      override def resolveClass(desc: ObjectStreamClass): Class[_] =
        Class.forName(desc.getName, false, loader)
    }

    val rereadObj = ois.readObject()
    rereadObj == obj
    rereadObj.toString == obj.toString
    rereadObj.asInstanceOf[T]
  }
}
