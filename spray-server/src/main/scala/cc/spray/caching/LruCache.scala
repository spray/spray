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
import akka.dispatch.{DefaultCompletableFuture, AlreadyCompletedFuture, CompletableFuture, Future}

object LruCache {
  def apply[V](maxEntries: Int = 500,
            dropFraction: Double = 0.20,
            ttl: Duration = 5.minutes) = {
    new LruCache[V](maxEntries, dropFraction, ttl)
  }
}

class LruCache[V](val maxEntries: Int,
                  val dropFraction: Double,
                  val ttl: Duration) extends Cache[V] { cache =>
  require(dropFraction > 0.0, "dropFraction must be > 0")

  sealed trait Entry {
    private var lastUsed = System.currentTimeMillis
    def refresh() { lastUsed = System.currentTimeMillis }
    def isAlive = (System.currentTimeMillis - lastUsed).millis < ttl // note that infinite Durations do not support .toMillis
  }
  case class ResponseEntry(value: V) extends Entry
  case class FutureEntry(future: Future[V]) extends Entry {
    override def toString = productPrefix
  }

  protected[caching] val store = new Store

  def get(key: Any) = store.getEntry(key).map {
    case ResponseEntry(response) => Right(response)
    case FutureEntry(future) => Left(future)
  }

  def supply(key: Any, func: CompletableFuture[V] => Unit): Future[V] = {
    store.getEntry(key) match {
      case Some(ResponseEntry(value)) => new AlreadyCompletedFuture[V](Right(value))
      case Some(FutureEntry(future)) => future
      case None => make(new DefaultCompletableFuture[V](Long.MaxValue)) { completableFuture => // TODO: make timeout configurable
        store.setEntry(key, FutureEntry(completableFuture))
        completableFuture.onComplete {
          _.value match {
            case Some(Right(value)) => store.setEntry(key, ResponseEntry(value))
            case Some(_) => store.deleteEntry(key) // in case of exceptions we simply deleteEntry the cache entry
            case None => throw new IllegalStateException // a completed future without value?
          }
        }
        func(completableFuture)
      }
    }
  }

  protected class Store extends LinkedHashMap[Any, Entry] {
    def getEntry(key: Any): Option[cache.Entry] = synchronized {
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
      synchronized {
        put(key, entry)
        if (size > maxEntries) {
          // remove the earliest entries
          val newSize = maxEntries - (maxEntries * dropFraction).toInt
          while (size > newSize) remove(firstEntry.key)
        }
      }
    }

    def deleteEntry(key: Any) {
      synchronized {
        remove(key)
      }
    }
  }
}