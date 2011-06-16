/*
 * Copyright (C) 2011 Mathias Doenitz
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

package cc.spray
package caching

import akka.dispatch.{CompletableFuture, Future}

trait Cache[V] {

  def apply(key: Any) = new Key(key)

  class Key(val key: Any) {
    def apply(func: CompletableFuture[V] => Unit) = supply(key, func)
    def apply(expr: => V): Future[V] = apply { completableFuture =>
      try {
        completableFuture.completeWithResult(expr)
      } catch {
        case e: Exception => completableFuture.completeWithException(e)
      }
    }
  }

  def get(key: Any): Option[Either[Future[V], V]]

  def supply(key: Any, func: CompletableFuture[V] => Unit): Future[V]
}