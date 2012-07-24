/*
 * Copyright (C) 2011-2012 spray.cc
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

package cc.spray.httpx.marshalling

import org.specs2.mutable.Specification
import akka.actor.ActorSystem
import cc.spray.http._


class StreamMarshallersSpec extends Specification {
  implicit val system = ActorSystem()

  "The streamMarshaller" should {
    "properly marshal a Stream instance" in {
      val stream = "abc" #:: "def" #:: "ghi" #:: "jkl" #:: Stream.empty
      val ctx = marshalCollecting(stream)
      ctx.entity === Some(HttpEntity("abc"))
      ctx.chunks.map(_.bodyAsString) === Seq("def", "ghi", "jkl")
      ctx.chunkedMessageEnd === Some(ChunkedMessageEnd())
    }
  }

  step(system.shutdown())

}