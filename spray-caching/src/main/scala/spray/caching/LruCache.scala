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

package spray.caching

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap
import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.concurrent.{ Promise, ExecutionContext, Future }
import scala.util.{ Failure, Success }
import spray.util.Timestamp

object LruCache {

  //# source-quote-LruCache-apply
  /**
   * Creates a new [[spray.caching.ExpiringLruCache]] or
   * [[spray.caching.SimpleLruCache]] instance depending on whether
   * a non-zero and finite timeToLive and/or timeToIdle is set or not.
   */
  def apply[V](maxCapacity: Int = 500,
               initialCapacity: Int = 16,
               timeToLive: Duration = Duration.Inf,
               timeToIdle: Duration = Duration.Inf): Cache[V] = {
    //#
    def check(dur: Duration, name: String) =
      require(dur != Duration.Zero,
        s"Behavior of LruCache.apply changed: Duration.Zero not allowed any more for $name parameter. To disable " +
          "expiration use Duration.Inf instead of Duration.Zero")
    // migration help
    check(timeToLive, "timeToLive")
    check(timeToIdle, "timeToIdle")

    if (timeToLive.isFinite() || timeToIdle.isFinite())
      new ExpiringLruCache[V](maxCapacity, initialCapacity, timeToLive, timeToIdle)
    else
      new SimpleLruCache[V](maxCapacity, initialCapacity)
  }
}

/**
 * A thread-safe implementation of [[spray.caching.cache]].
 * The cache has a defined maximum number of entries it can store. After the maximum capacity is reached new
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

  def apply(key: Any, genValue: () ⇒ Future[V])(implicit ec: ExecutionContext): Future[V] = {
    val promise = Promise[V]()
    store.putIfAbsent(key, promise.future) match {
      case null ⇒
        val future = genValue()
        future.onComplete { value ⇒
          promise.complete(value)
          // in case of exceptions we remove the cache entry (i.e. try again later)
          if (value.isFailure) store.remove(key, promise.future)
        }
        future
      case existingFuture ⇒ existingFuture
    }
  }

  def remove(key: Any) = Option(store.remove(key))

  def clear(): Unit = { store.clear() }

  def size = store.size
}

/**
 * A thread-safe implementation of [[spray.caching.cache]].
 * The cache has a defined maximum number of entries is can store. After the maximum capacity has been reached new
 * entries cause old ones to be evicted in a last-recently-used manner, i.e. the entries that haven't been accessed for
 * the longest time are evicted first.
 * In addition this implementation optionally supports time-to-live as well as time-to-idle expiration.
 * The former provides an upper limit to the time period an entry is allowed to remain in the cache while the latter
 * limits the maximum time an entry is kept without having been accessed. If both values are non-zero the time-to-live
 * has to be strictly greater than the time-to-idle.
 * Note that expired entries are only evicted upon next access (or by being thrown out by the capacity constraint), so
 * they might prevent gargabe collection of their values for longer than expected.
 *
 * @param timeToLive the time-to-live in millis, zero for disabling ttl-expiration
 * @param timeToIdle the time-to-idle in millis, zero for disabling tti-expiration
 */
final class ExpiringLruCache[V](maxCapacity: Long, initialCapacity: Int,
                                timeToLive: Duration, timeToIdle: Duration) extends Cache[V] {
  require(!timeToLive.isFinite || !timeToIdle.isFinite || timeToLive > timeToIdle,
    s"timeToLive($timeToLive) must be greater than timeToIdle($timeToIdle)")

  private[caching] val store = new ConcurrentLinkedHashMap.Builder[Any, Entry[V]]
    .initialCapacity(initialCapacity)
    .maximumWeightedCapacity(maxCapacity)
    .build()

  @tailrec
  def get(key: Any): Option[Future[V]] = store.get(key) match {
    case null ⇒ None
    case entry if (isAlive(entry)) ⇒
      entry.refresh()
      Some(entry.future)
    case entry ⇒
      // remove entry, but only if it hasn't been removed and reinserted in the meantime
      if (store.remove(key, entry)) None // successfully removed
      else get(key) // nope, try again
  }

  def apply(key: Any, genValue: () ⇒ Future[V])(implicit ec: ExecutionContext): Future[V] = {
    def insert() = {
      val newEntry = new Entry(Promise[V]())
      val valueFuture =
        store.put(key, newEntry) match {
          case null ⇒ genValue()
          case entry ⇒
            if (isAlive(entry)) {
              // we date back the new entry we just inserted
              // in the meantime someone might have already seen the too fresh timestamp we just put in,
              // but since the original entry is also still alive this doesn't matter
              newEntry.created = entry.created
              entry.future
            } else genValue()
        }
      valueFuture.onComplete { value ⇒
        newEntry.promise.tryComplete(value)
        // in case of exceptions we remove the cache entry (i.e. try again later)
        if (value.isFailure) store.remove(key, newEntry)
      }
      newEntry.promise.future
    }
    store.get(key) match {
      case null ⇒ insert()
      case entry if (isAlive(entry)) ⇒
        entry.refresh()
        entry.future
      case entry ⇒ insert()
    }
  }

  def remove(key: Any) = store.remove(key) match {
    case null                      ⇒ None
    case entry if (isAlive(entry)) ⇒ Some(entry.future)
    case entry                     ⇒ None
  }

  def clear(): Unit = { store.clear() }

  def size = store.size

  private def isAlive(entry: Entry[V]) =
    (entry.created + timeToLive).isFuture &&
      (entry.lastAccessed + timeToIdle).isFuture
}

private[caching] class Entry[T](val promise: Promise[T]) {
  @volatile var created = Timestamp.now
  @volatile var lastAccessed = Timestamp.now
  def future = promise.future
  def refresh(): Unit = {
    // we dont care whether we overwrite a potentially newer value
    lastAccessed = Timestamp.now
  }
  override def toString = future.value match {
    case Some(Success(value))     ⇒ value.toString
    case Some(Failure(exception)) ⇒ exception.toString
    case None                     ⇒ "pending"
  }
}
