/*
 * Copyright (C) 2011, 2012 Mathias Doenitz
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

package cc.spray.can.util

import annotation.tailrec

private[can] object LinkedList {
  trait Element[Elem >: Null <: Element[Elem]] {
    private[LinkedList] var list: LinkedList[Elem] = _
    private[LinkedList] var prev: Elem = _
    private[LinkedList] var next: Elem = _
    private[LinkedList] var timeStamp: Long = _

    def memberOf: LinkedList[Elem] = list
  }
}

// a special mutable, double-linked list without "buckets", i.e. container objects to hold the payload;
// rather the payload objects themselves must contain the link fields, which has the advantage of fewer created objects
// and, more importantly, constant-time complexity for removal operations
private[can] class LinkedList[Elem >: Null <: LinkedList.Element[Elem]] {
  private var first: Elem = _
  private var last: Elem = _
  private var length: Int = _

  def size = length

  def += (rec: Elem) {
    require(rec.list == null, "Cannot add an element that is already member of some list")
    assert(rec.prev == null && rec.next == null)
    if (length == 0) {
      first = rec
      last = rec
    } else {
      last.next = rec
      rec.prev = last
      last = rec
    }
    rec.list = this
    rec.timeStamp = System.currentTimeMillis
    length += 1
  }

  def -= (rec: Elem) {
    require(rec.list == this, "Cannot remove an element that is not part of this list")
    if (rec == last) {
      if (rec == first) {
        first = null
        last = null
      } else {
        last = rec.prev
        last.next = null
      }
    } else if (rec == first) {
      first = rec.next
      first.prev = null
    } else {
      assert(rec.prev != null && rec.next != null)
      rec.prev.next = rec.next
      rec.next.prev = rec.prev
    }
    rec.list = null
    rec.prev = null
    rec.next = null
    length -= 1
  }

  def refresh(rec: Elem) {
    if (rec.list != null) {
      this -= rec
      this += rec
    }
  }

  def forAllTimedOut[U](timeout: Long)(f: Elem => U) {
    val now = System.currentTimeMillis
    traverse { elem =>
      if (now - elem.timeStamp > timeout) {
        f(elem)
        true // continue the traversal
      } else false // once we reached an element that hasn't timed out yet all subsequent ones won't either
    }
  }

  /**
   * Applies the given function to all elements in sequence.
   * The function can stop the traversal by returning false.
   */
  def traverse(f: Elem => Boolean) {
    @tailrec def traverse(e: Elem) {
      if (e != null) {
        val next = e.next // get the next element before applying the function, since the latter might move the element
        if (f(e)) traverse(next)
      }
    }
    traverse(first)
  }

  override def toString = {
    val sb = new java.lang.StringBuilder("[")
    traverse { elem =>
      if (sb.length > 1) sb.append(", ")
      sb.append(elem)
      true
    }
    sb.append(']').toString
  }
}


