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

package spray.io

import org.specs2.mutable.Specification
import scala.util.Random
import akka.dispatch.Future
import akka.actor.ActorSystem
import spray.util._

class SslBufferPoolSpec extends Specification {
  val system = ActorSystem("SslBufferPoolSpec")
  implicit val dispatcher = system.dispatcher

  "The SslBufferPool" should {
    "provide a proper, thread-safe buffer pool" in {
      Future.traverse((1 to 100).toList) { i ⇒
        Future {
          val buf = SslBufferPool.acquire()
          val nonce = Random.alphanumeric.take(16).mkString
          buf.put(nonce.getBytes)
          buf.flip()
          val buf2 = SslBufferPool.acquire()
          buf2.put(buf)
          buf2.flip()
          val result = buf2.drainToString
          SslBufferPool.release(buf)
          SslBufferPool.release(buf2)
          result -> nonce
        }
      }.await.map(t ⇒ t._1 === t._2).reduceLeft(_ and _)
    }
  }

  step(system.shutdown())
}
