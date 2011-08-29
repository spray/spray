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

package cc.spray.can.utils

import annotation.tailrec

trait LinkedListElement[Elem <: LinkedListElement[Elem]] {
  var prev: Elem = _
  var next: Elem = _
}

// a special mutable, double-linked list without "buckets", i.e. container objects to hold the payload;
// rather the payload objects themselves must contain the link fields, which has the advantage that removal operations
// are constant-time operations
class LinkedList[Elem >: Null <: LinkedListElement[Elem]] {
  var first: Elem = _
  var last: Elem = _
  var size: Int = _

  def += (rec: Elem) {
    if (size == 0) {
      first = rec
      last = rec
    } else {
      last.next = rec
      rec.prev = last
      last = rec
    }
    size += 1
  }

  def -= (rec: Elem) {
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
      rec.prev.next = rec.next
      rec.next.prev = rec.prev
    }
    rec.prev = null
    rec.next = null
    size -= 1
  }

  def moveToEnd(rec: Elem) {
    this -= rec
    this += rec
  }

  /**
   * Applies the given function to all elements in sequence.
   * The function can stop the traversal by returning false.
   */
  def traverse(f: Elem => Boolean) {
    @tailrec def traverse(e: Elem) {
      if (e != null && f(e)) traverse(e.next)
    }
    traverse(first)
  }

  override def toString = {
    val sb = new java.lang.StringBuilder("[")
    traverse { elem =>
      if (sb.length > 1) sb.append(',')
      sb.append(elem)
      true
    }
    sb.append(']').toString
  }
}


