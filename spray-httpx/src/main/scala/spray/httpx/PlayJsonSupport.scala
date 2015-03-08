/*
 * Copyright (C) 2011-2015 spray.io
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

package spray.httpx

import play.api.libs.json._
import play.api.libs.functional._
import spray.httpx.marshalling.Marshaller
import scala.util.control.Exception.catching
import scala.util.control.NonFatal
import spray.httpx.unmarshalling.{ Deserialized, MalformedContent, SimpleUnmarshaller, Unmarshaller }
import spray.http._
import MediaTypes._

/**
 * A trait providing automatic to and from JSON marshalling/unmarshalling using in-scope *play-json* Reads/Writes.
 * Note that *spray-httpx* does not have an automatic dependency on *play-json*.
 * You'll need to provide the appropriate *play-json* artifacts yourself.
 */
trait PlayJsonSupport {
  implicit def playJsonUnmarshaller[T: Reads] =
    delegate[String, T](`application/json`)(string ⇒
      try {
        implicitly[Reads[T]].reads(Json.parse(string)).asEither.left.map(e ⇒ MalformedContent(s"Received JSON is not valid.\n${Json.prettyPrint(JsError.toFlatJson(e))}"))
      } catch {
        case NonFatal(exc) ⇒ Left(MalformedContent(exc.getMessage, exc))
      })(UTF8StringUnmarshaller)

  implicit def playJsonMarshaller[T: Writes](implicit printer: JsValue ⇒ String = Json.stringify) =
    Marshaller.delegate[T, String](ContentTypes.`application/json`) { value ⇒
      printer(implicitly[Writes[T]].writes(value))
    }

  //
  private val UTF8StringUnmarshaller = new Unmarshaller[String] {
    def apply(entity: HttpEntity) = Right(entity.asString(defaultCharset = HttpCharsets.`UTF-8`))
  }

  // Unmarshaller.delegate is used as a kind of map operation; play-json JsResult can contain either validation errors or the JsValue
  // representing a JSON object. We need a delegate method that works as a flatMap and let the provided A ⇒ Deserialized[B] function
  // to deal with any possible error, including exceptions.
  //
  private def delegate[A, B](unmarshalFrom: ContentTypeRange*)(f: A ⇒ Deserialized[B])(implicit ma: Unmarshaller[A]): Unmarshaller[B] =
    new SimpleUnmarshaller[B] {
      val canUnmarshalFrom = unmarshalFrom
      def unmarshal(entity: HttpEntity) = ma(entity).right.flatMap(a ⇒ f(a))
    }
}

object PlayJsonSupport extends PlayJsonSupport
