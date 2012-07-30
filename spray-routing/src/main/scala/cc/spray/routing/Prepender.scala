/*
 * Copyright (C) 2011-2012 spray.cc
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

package cc.spray.routing

import shapeless._

// temporary patch layer required until https://github.com/milessabin/shapeless/pull/9 has made it into a release

trait Prepender[L <: HList, R <: HList] {
  type Out <: HList
  def apply(prefix : L, suffix : R) : Out
}

object Prepender extends PrependerLowerPrioImplicits1 {
  implicit def hnilPrepender1[L <: HList, R <: HNil] = new Prepender[L, R] {
    type Out = L
    def apply(prefix: L, suffix: R) = prefix
  }
}

private[routing] abstract class PrependerLowerPrioImplicits1 extends PrependerLowerPrioImplicits2 {
  implicit def hnilPrepender2[L <: HNil, R <: HList] = new Prepender[L, R] {
    type Out = R
    def apply(prefix: L, suffix: R) = suffix
  }
}

private[routing] abstract class PrependerLowerPrioImplicits2 {
  implicit def defaultPrepender[L <: HList, R <: HList, Out0 <: HList]
                               (implicit p: PrependAux[L, R, Out0]) = new Prepender[L, R] {
    type Out = Out0
    def apply(prefix: L, suffix: R) = p(prefix, suffix)
  }
}
