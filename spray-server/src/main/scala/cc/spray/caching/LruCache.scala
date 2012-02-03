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

import akka.util.Duration
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap
import annotation.tailrec
import akka.dispatch.{CompletableFuture, DefaultCompletableFuture, Future}

object LruCache {
  /**
   * Creates a new instance of either []cc.spray.caching.ExpiringLruCache]] or [[cc.spray.caching.SimpleLruCache]],
   * depending on whether a timeToIdle is set or not.
   */
  def apply[V](maxCapacity: Int = 500, initialCapacity: Int = 16, timeToIdle: Option[Duration] = None): Cache[V] = {
    timeToIdle match {
      case Some(duration) =>
        new ExpiringLruCache[V](maxCapacity, initialCapacity, if (duration.finite_?) duration.toMillis else 0)
      case None =>
        new SimpleLruCache[V](maxCapacity, initialCapacity)
    }
  }
}

/**
 * A thread-safe implementation of [[cc.spray.caching.cache]].
 * The cache has a defined maximum number of entries is can store. After the maximum capacity has been reached new
 * entries cause old ones to be evicted in a last-recently-used manner, i.e. the entries that haven't been accessed for
 * the longest time are evicted first.
 */
final class SimpleLruCache[V](val maxCapacity: Int, val initialCapacity: Int) extends Cache[V] {
  require(maxCapacity >= 0, "maxCapacity must not be negative")
  require(initialCapacity <= maxCapacity, "initialCapacity must be <= maxCapacity")

  private[caching] val store = new ConcurrentLinkedHashMap.Builder[Any, Future[V]]
      .initialCapacity(initialCapacity)
      .maximumWeightedCapacity(maxCapacity)
      .build()

  def get(key: Any) = Option(store.get(key))

  def fromFuture(key: Any)(future: => Future[V]): Future[V] = {
    val newFuture = new DefaultCompletableFuture[V](Long.MaxValue)
    store.putIfAbsent(key, newFuture) match {
      case null => future.onComplete(f => newFuture.complete(f.value.get))
      case existingFuture => existingFuture
    }
  }

  def remove(key: Any) = Option(store.remove(key))

  def clear() { store.clear() }
}

/**
 * A thread-safe implementation of [[cc.spray.caching.cache]].
 * The cache has a defined maximum number of entries is can store. After the maximum capacity has been reached new
 * entries cause old ones to be evicted in a last-recently-used manner, i.e. the entries that haven't been accessed for
 * the longest time are evicted first.
 * In addition this implementation supports time-to-idle expiration. The time-to-idle duration indicates how long the
 * entry can stay without having been accessed.
 * Note that expired entries are only evicted upon next access (or by being throws out by the capacity constraint), so
 * they might prevent gargabe collection of their values for longer than expected.
 *
 * @param timeToIdle the time-to-idle in millis, if zero time-to-idle expiration is disabled
 */
final class ExpiringLruCache[V](maxCapacity: Int, initialCapacity: Int, timeToIdle: Long) extends Cache[V] {
  require(timeToIdle >= 0, "timeToIdle must not be negative")

  private[caching] val store = new ConcurrentLinkedHashMap.Builder[Any, Entry[V]]
    .initialCapacity(initialCapacity)
    .maximumWeightedCapacity(maxCapacity)
    .build()

  @tailrec
  def get(key: Any): Option[Future[V]] = store.get(key) match {
    case null => None
    case entry if (isAlive(entry)) =>
      entry.refresh()
      Some(entry.future)
    case entry =>
      // remove entry, but only if it hasn't been removed and reinserted in the meantime
      if (store.remove(key, entry)) None // successfully removed
      else get(key) // nope, try again
  }

  def fromFuture(key: Any)(future: => Future[V]): Future[V] = {
    def insert() = {
      val newEntry = new Entry(new DefaultCompletableFuture[V](Long.MaxValue))
      val valueFuture = store.put(key, newEntry) match {
        case null => future
        case entry => if (isAlive(entry)) entry.future else future
      }
      valueFuture.onComplete(f => newEntry.future.complete(f.value.get))
    }
    store.get(key) match {
      case null => insert()
      case entry if (isAlive(entry)) =>
        entry.refresh()
        entry.future
      case entry => insert()
    }
  }

  def remove(key: Any) = store.remove(key) match {
    case null => None
    case entry if (isAlive(entry)) => Some(entry.future)
    case entry => None
  }

  def clear() { store.clear() }

  private def isAlive(entry: Entry[V]) =
    timeToIdle == 0 || (System.currentTimeMillis - entry.lastAccessed) < timeToIdle
}

private[caching] class Entry[T](val future: CompletableFuture[T]) {
  @volatile var lastAccessed = System.currentTimeMillis
  def refresh() {
    // we dont care whether we overwrite a potentially newer value
    lastAccessed = System.currentTimeMillis
  }
  override def toString = future.value match {
    case Some(Right(value)) => value.toString
    case Some(Left(exception)) => exception.toString
    case None => "pending"
  }
}