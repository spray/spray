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

package spray.httpx.marshalling

import scala.xml.NodeSeq
import org.specs2.mutable.Specification
import akka.actor.ActorSystem
import spray.http._
import MediaTypes._
import HttpCharsets._

class MetaMarshallersSpec extends Specification {
  implicit val system = ActorSystem()

  "The eitherMarshaller" should {
    "properly marshal an Either instance" in {
      type MyEither = Either[Throwable, String]
      marshal[MyEither](Right("Yes")) === Right(HttpEntity("Yes"))
      val exception = new RuntimeException("Naa!")
      marshal[MyEither](Left(exception)) === Left(exception)
    }
  }

  "The streamMarshaller" should {
    "properly marshal a Stream instance" in {
      val stream = "abc" #:: "def" #:: "ghi" #:: "jkl" #:: Stream.empty
      val ctx = new CollectingMarshallingContext
      marshalCollecting(stream, ctx)
      ctx.entity === Some(HttpEntity("abc"))
      ctx.chunks.map(_.data.asString) === Seq("def", "ghi", "jkl")
      ctx.chunkedMessageEnd === Some(ChunkedMessageEnd)
    }
    "properly marshal an empty Stream" in {
      val ctx = new CollectingMarshallingContext
      marshalCollecting(Stream.empty[String], ctx)
      ctx.entity === Some(HttpEntity.Empty)
    }
  }

  "MMarshallers" should {
    "allow provision of Marshaller[M[T]] given a MarshallerM[M]" in {
      class CakeLayer[M[_]](implicit marshallerM: MarshallerM[M]) {
        def apply(value: Either[M[NodeSeq], M[String]]): HttpEntity =
          value match {
            case Left(mn)  ⇒ marshalUnsafe(mn) // requires a Marshaller[M[NodeSeq]]
            case Right(ms) ⇒ marshalUnsafe(ms) // requires a Marshaller[M[String]]
          }
      }
      val optionCakeLayer = new CakeLayer[Option]
      optionCakeLayer(Right(Some("foo"))) === HttpEntity("foo")
      optionCakeLayer(Right(None)) === HttpEntity.Empty
      optionCakeLayer(Left(Some(<i>42</i>))) === HttpEntity(ContentType(`text/xml`, `UTF-8`), "<i>42</i>")
      optionCakeLayer(Left(None)) === HttpEntity.Empty
    }
  }

  step(system.shutdown())
}