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

package spray.caching

import akka.dispatch.{ Promise, Future, ExecutionContext }
import spray.util.tryOrElse

/**
 * General interface implemented by all spray cache implementations.
 */
trait Cache[V] {

  /**
   * Selects the (potentially non-existing) cache entry with the given key.
   */
  def apply(key: Any) = new Key(key)

  class Key(key: Any) {

    /**
     * Wraps the given expression with caching support.
     */
    def apply(expr: ⇒ V)(implicit ec: ExecutionContext): Future[V] = apply { promise ⇒
      tryOrElse(promise.success(expr), promise.failure)
    }

    /**
     * Wraps the given function with caching support.
     */
    def apply(func: Promise[V] ⇒ Unit)(implicit executor: ExecutionContext): Future[V] = fromFuture(key) {
      val p = Promise[V]()
      func(p)
      p.future
    }
  }

  /**
   * Retrieves the future instance that is currently in the cache for the given key.
   * Returns None if the key has no corresponding cache entry.
   */
  def get(key: Any): Option[Future[V]]

  /**
   * Supplies a cache entry for the given key from the given expression.
   */
  def fromFuture(key: Any)(future: ⇒ Future[V])(implicit executor: ExecutionContext): Future[V]

  /**
   * Removes the cache item for the given key. Returns the removed item if it was found (and removed).
   */
  def remove(key: Any): Option[Future[V]]

  /**
   * Clears the cache by removing all entries.
   */
  def clear()
}
