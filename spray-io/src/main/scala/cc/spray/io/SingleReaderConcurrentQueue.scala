/*
 * Copyright (C) 2011-2012 spray.io
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

import java.util.concurrent.atomic.AtomicReference
import annotation.tailrec

/**
 * A fast, lock-free, unbounded FIFO queue implementation that supports multiple concurrent writers (i.e. "enqueuers")
 * and one reader (i.e. "dequeuer"). The queue must be dequeued by the thread having created the queue (the reader
 * thread). Only the "enqueue" method is allowed to be called by multiple concurrent threads, all other methods only
 * produce meaningful results when called from the single reader thread.
 * Enqueuing always happens in constant time, dequeueing is performed in O(1) in most cases, sometimes (in one n-th
 * of all cases) it takes O(n), which still constitutes asymptotic O(1) complexity.
 */
final class SingleReaderConcurrentQueue[T] {
  private val in = new AtomicReference(List.empty[T])
  private var out = List.empty[T]

  @tailrec
  def enqueue(value: T) {
    val currentIn = in.get()
    val newIn = value :: currentIn
    if (!in.compareAndSet(currentIn, newIn))
      enqueue(value) // retry
  }

  def isEmpty = out.isEmpty && in.get.isEmpty

  def dequeue(): T = {
    val tail = if (out.isEmpty) in.getAndSet(Nil).reverse else out
    out = tail.tail
    tail.head
  }

}