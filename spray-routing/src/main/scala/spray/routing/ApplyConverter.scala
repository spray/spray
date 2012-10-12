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

package spray.routing

import shapeless._


sealed abstract class ApplyConverter[L <: HList] {
  type In
  def apply(f: In): L => Route
}

object ApplyConverter {
  implicit val hac0 = new ApplyConverter[HNil] {
    type In = Route
    def apply(fn: In) = {
      case HNil => fn
    }
  }

  implicit def hac1[A] = new ApplyConverter[A :: HNil] {
    type In = A => Route
    def apply(fn: In) = {
      case a :: HNil => fn(a)
    }
  }

  implicit def hac2[A, B] = new ApplyConverter[A :: B :: HNil] {
    type In = (A, B) => Route
    def apply(fn: In) = {
      case a :: b :: HNil => fn(a, b)
    }
  }

  implicit def hac3[A, B, C] = new ApplyConverter[A :: B :: C :: HNil] {
    type In = (A, B, C) => Route
    def apply(fn: In) = {
      case a :: b :: c :: HNil => fn(a, b, c)
    }
  }

  implicit def hac4[A, B, C, D] = new ApplyConverter[A :: B :: C :: D :: HNil] {
    type In = (A, B, C, D) => Route
    def apply(fn: In) = {
      case a :: b :: c :: d :: HNil => fn(a, b, c, d)
    }
  }

  implicit def hac5[A, B, C, D, E] = new ApplyConverter[A :: B :: C :: D :: E :: HNil] {
    type In = (A, B, C, D, E) => Route
    def apply(fn: In) = {
      case a :: b :: c :: d :: e :: HNil => fn(a, b, c, d, e)
    }
  }

  implicit def hac6[A, B, C, D, E, F] = new ApplyConverter[A :: B :: C :: D :: E :: F :: HNil] {
    type In = (A, B, C, D, E, F) => Route
    def apply(fn: In) = {
      case a :: b :: c :: d :: e :: f :: HNil => fn(a, b, c, d, e, f)
    }
  }

  implicit def hac7[A, B, C, D, E, F, G] = new ApplyConverter[A :: B :: C :: D :: E :: F :: G :: HNil] {
    type In = (A, B, C, D, E, F, G) => Route
    def apply(fn: In) = {
      case a :: b :: c :: d :: e :: f :: g :: HNil => fn(a, b, c, d, e, f, g)
    }
  }

  implicit def hac8[A, B, C, D, E, F, G, H] = new ApplyConverter[A :: B :: C :: D :: E :: F :: G :: H :: HNil] {
    type In = (A, B, C, D, E, F, G, H) => Route
    def apply(fn: In) = {
      case a :: b :: c :: d :: e :: f :: g :: h :: HNil => fn(a, b, c, d, e, f, g, h)
    }
  }

  implicit def hac9[A, B, C, D, E, F, G, H, I] = new ApplyConverter[A :: B :: C :: D :: E :: F :: G :: H :: I :: HNil] {
    type In = (A, B, C, D, E, F, G, H, I) => Route
    def apply(fn: In) = {
      case a :: b :: c :: d :: e :: f :: g :: h :: i :: HNil => fn(a, b, c, d, e, f, g, h, i)
    }
  }

  implicit def hac10[A, B, C, D, E, F, G, H, I, J] = new ApplyConverter[A :: B :: C :: D :: E :: F :: G :: H :: I :: J :: HNil] {
    type In = (A, B, C, D, E, F, G, H, I, J) => Route
    def apply(fn: In) = {
      case a :: b :: c :: d :: e :: f :: g :: h :: i :: j :: HNil => fn(a, b, c, d, e, f, g, h, i, j)
    }
  }

  implicit def hac11[A, B, C, D, E, F, G, H, I, J, K] = new ApplyConverter[A :: B :: C :: D :: E :: F :: G :: H :: I :: J :: K :: HNil] {
    type In = (A, B, C, D, E, F, G, H, I, J, K) => Route
    def apply(fn: In) = {
      case a :: b :: c :: d :: e :: f :: g :: h :: i :: j :: k :: HNil => fn(a, b, c, d, e, f, g, h, i, j, k)
    }
  }

  implicit def hac12[A, B, C, D, E, F, G, H, I, J, K, L] = new ApplyConverter[A :: B :: C :: D :: E :: F :: G :: H :: I :: J :: K :: L :: HNil] {
    type In = (A, B, C, D, E, F, G, H, I, J, K, L) => Route
    def apply(fn: In) = {
      case a :: b :: c :: d :: e :: f :: g :: h :: i :: j :: k :: l :: HNil => fn(a, b, c, d, e, f, g, h, i, j, k, l)
    }
  }

  implicit def hac13[A, B, C, D, E, F, G, H, I, J, K, L, M] = new ApplyConverter[A :: B :: C :: D :: E :: F :: G :: H :: I :: J :: K :: L :: M :: HNil] {
    type In = (A, B, C, D, E, F, G, H, I, J, K, L, M) => Route
    def apply(fn: In) = {
      case a :: b :: c :: d :: e :: f :: g :: h :: i :: j :: k :: l :: m :: HNil => fn(a, b, c, d, e, f, g, h, i, j, k, l, m)
    }
  }

  implicit def hac14[A, B, C, D, E, F, G, H, I, J, K, L, M, N] = new ApplyConverter[A :: B :: C :: D :: E :: F :: G :: H :: I :: J :: K :: L :: M :: N :: HNil] {
    type In = (A, B, C, D, E, F, G, H, I, J, K, L, M, N) => Route
    def apply(fn: In) = {
      case a :: b :: c :: d :: e :: f :: g :: h :: i :: j :: k :: l :: m :: n :: HNil => fn(a, b, c, d, e, f, g, h, i, j, k, l, m, n)
    }
  }

  implicit def hac15[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O] = new ApplyConverter[A :: B :: C :: D :: E :: F :: G :: H :: I :: J :: K :: L :: M :: N :: O :: HNil] {
    type In = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O) => Route
    def apply(fn: In) = {
      case a :: b :: c :: d :: e :: f :: g :: h :: i :: j :: k :: l :: m :: n :: o :: HNil => fn(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o)
    }
  }

}
