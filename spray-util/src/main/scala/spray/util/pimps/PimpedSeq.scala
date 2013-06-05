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

package spray.util.pimps

import collection.LinearSeq
import annotation.tailrec

abstract class PimpedSeq[+A] {
  /**
   * Returns the first defined result of the given function when applied to the underlying sequence (in order) or
   * `None`, if the given function returns `None` for all elements of the underlying sequence.
   */
  def mapFind[B](f: A => Option[B]): Option[B]

  /**
   * Returns the first result of the given partial function when applied to the underlying sequence (in order) or
   * `None`, if the partial function is not defined for any object of the sequence.
   */
  def mapFindPF[B](f: PartialFunction[A, B]): Option[B]

  /**
   * Returns the first object of type B in the underlying sequence or `None`, if none is found.
   */
  def findByType[B: ClassManifest]: Option[B] = {
    val erasure = classManifest.erasure
    mapFind(x => if (erasure.isInstance(x)) Some(x.asInstanceOf[B]) else None)
  }
}

class PimpedLinearSeq[+A](underlying: LinearSeq[A]) extends PimpedSeq[A] {

  def mapFind[B](f: A => Option[B]): Option[B] = {
    @tailrec
    def mapFind(seq: LinearSeq[A]): Option[B] = {
      if (!seq.isEmpty) {
        f(seq.head) match {
          case x: Some[_] => x
          case None => mapFind(seq.tail)
        }
      } else None
    }
    mapFind(underlying)
  }

  def mapFindPF[B](f: PartialFunction[A, B]): Option[B] = {
    @tailrec
    def mapFind(seq: LinearSeq[A]): Option[B] = {
      if (!seq.isEmpty) {
        if (f.isDefinedAt(seq.head)) Some(f(seq.head))
        else mapFind(seq.tail)
      } else None
    }
    mapFind(underlying)
  }
}

class PimpedIndexedSeq[+A](underlying: IndexedSeq[A]) extends PimpedSeq[A] {

  def mapFind[B](f: A => Option[B]): Option[B] = {
    @tailrec
    def mapFind(ix: Int): Option[B] = {
      if (ix < underlying.length) {
        f(underlying(ix)) match {
          case x: Some[_] => x
          case None => mapFind(ix + 1)
        }
      } else None
    }
    mapFind(0)
  }

  def mapFindPF[B](f: PartialFunction[A, B]): Option[B] = {
    @tailrec
    def mapFind(ix: Int): Option[B] = {
      if (ix < underlying.length) {
        val x = underlying(ix)
        if (f.isDefinedAt(x)) Some(f(x))
        else mapFind(ix + 1)
      } else None
    }
    mapFind(0)
  }
}