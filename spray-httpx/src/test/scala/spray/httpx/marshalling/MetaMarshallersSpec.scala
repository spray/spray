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

package spray.httpx.marshalling

import org.specs2.mutable.Specification
import akka.actor.ActorSystem
import spray.http._

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
      val ctx = marshalCollecting(stream)
      ctx.entity === Some(HttpEntity("abc"))
      ctx.chunks.map(_.data.asString) === Seq("def", "ghi", "jkl")
      ctx.chunkedMessageEnd === Some(ChunkedMessageEnd)
    }
  }

  step(system.shutdown())
}