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
import collection.mutable.LinkedHashMap
import akka.dispatch.{AlreadyCompletedFuture, CompletableFuture, Future}

object LruCache {
  def apply[V](maxEntries: Int = 500, dropFraction: Double = 0.20, ttl: Duration = 5.minutes) = {
    new LruCache[V](maxEntries, dropFraction, ttl)
  }
}

class LruCache[V](val maxEntries: Int, val dropFraction: Double, val ttl: Duration) extends Cache[V] { cache =>
  require(dropFraction > 0.0, "dropFraction must be > 0")

  class Entry(val future: Future[V]) {
    private var lastUsed = System.currentTimeMillis
    def refresh() { lastUsed = System.currentTimeMillis }
    def isAlive = (System.currentTimeMillis - lastUsed).millis < ttl // note that infinite Durations do not support .toMillis
    override def toString = future.value match {
      case Some(Right(value)) => value.toString
      case Some(Left(exception)) => exception.toString
      case None => "pending"
    }
  }

  protected[caching] val store = new Store

  def get(key: Any) = synchronized {
    store.getEntry(key).map(_.future)
  }

  def fromFuture(key: Any)(completableFuture: => Future[V]): Future[V] = synchronized {
    store.getEntry(key) match {
      case Some(entry) => entry.future
      case None => {
        val future = completableFuture
        store.setEntry(key, new Entry(future))
        future.onComplete {
          _.value.get match {
            case Right(value) => store.setEntry(key, new Entry(new AlreadyCompletedFuture[V](Right(value))))
            case _ => store.remove(key) // in case of exceptions we remove the cache entry (i.e. try again later)
          }
        }
      }
    }
  }

  protected class Store extends LinkedHashMap[Any, Entry] {
    def getEntry(key: Any): Option[cache.Entry] = {
      get(key) match {
        case Some(entry) => {
          if (entry.isAlive) {
            entry.refresh()
            remove(key)       // TODO: replace with optimized "refresh" implementation
            put(key, entry)
            Some(entry)
          } else {
            // entry expired, so remove this one and all earlier ones (they have expired as well)
            while (firstEntry.key != key) remove(firstEntry.key)
            remove(key)
            None
          }
        }
        case None => None
      }
    }

    def setEntry(key: Any, entry: cache.Entry) {
      put(key, entry)
      if (size > maxEntries) {
        // remove the earliest entries
        val newSize = maxEntries - (maxEntries * dropFraction).toInt
        while (size > newSize) remove(firstEntry.key)
      }
    }
  }
}