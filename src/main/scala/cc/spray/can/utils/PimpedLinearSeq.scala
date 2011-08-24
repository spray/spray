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

import collection.immutable.LinearSeq
import annotation.tailrec

class PimpedLinearSeq[+A](underlying: LinearSeq[A]) {
  
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
  
  def findByType[B :ClassManifest]: Option[B] = {
    val erasure = classManifest.erasure
    mapFind { x =>
      if (erasure.isInstance(x)) Some(x.asInstanceOf[B]) else None
    }
  }
  
}