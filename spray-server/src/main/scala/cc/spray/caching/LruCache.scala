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

import akka.util.duration._
import akka.util.Duration
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap
import annotation.tailrec
import akka.dispatch.{CompletableFuture, DefaultCompletableFuture, Future}

object LruCache {
  /**
   * Creates a new LruCache instance
   */
  def apply[V](maxEntries: Int = 500, initialCapacity: Int = 16, ttl: Duration = 5.minutes) = {
    new LruCache[V](maxEntries, initialCapacity, ttl)
  }
}

/**
 * A last-recently-used cache with a defined capacity and time-to-live.
 */
class LruCache[V](val maxEntries: Int, val initialCapacity: Int, val ttl: Duration) extends Cache[V] { cache =>
  require(ttl.length >= 0, "ttl must not be negative")

  private[caching] val store = new ConcurrentLinkedHashMap.Builder[Any, Entry[V]]
    .initialCapacity(initialCapacity)
    .maximumWeightedCapacity(maxEntries)
    .build()

  @tailrec
  final def get(key: Any): Option[Future[V]] = store.get(key) match {
    case null => None
    case entry =>
      if (entry.isAlive(ttl)) {
        store.put(key, Entry(entry.future)) // refresh
        Some(entry.future)
      } else {
        // remove entry, but only if it hasn't been refreshed in the meantime
        if (store.remove(key, entry)) None // successfully removed
        else get(key) // nope, try again
      }
  }

  def fromFuture(key: Any)(future: => Future[V]): Future[V] = {
    val newEntry = Entry(new DefaultCompletableFuture[V](Long.MaxValue))
    val previousFuture = store.put(key, newEntry) match {
      case null => future
      case entry => if (entry.isAlive(ttl)) entry.future else future
    }
    previousFuture.onComplete(f => newEntry.future.complete(f.value.get))
  }
}

private[caching] case class Entry[T](future: CompletableFuture[T]) {
  val created = System.currentTimeMillis
  def isAlive(ttl: Duration) =
    (System.currentTimeMillis - created).millis < ttl // note that infinite Durations do not support .toMillis
  override def toString = future.value match {
    case Some(Right(value)) => value.toString
    case Some(Left(exception)) => exception.toString
    case None => "pending"
  }
}