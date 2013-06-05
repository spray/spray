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

import spray.httpx.unmarshalling.{MalformedContent, DeserializationError, Deserializer}
import shapeless._

// TODO: simplify by rebasing on a shapeless fold
// I don't think we can get around spelling out 22 different cases without giving up on our short
// directive.as(CaseClass) notation (since we have to provide a dedicated magnet for the proper
// apply function type (e.g. (A, B, C) => CC), but we might be able to simplify the implementation
// of the 22 cases by converting into an HList that can then be mapped/folded over

trait HListDeserializer[L <: HList, T] extends Deserializer[L, T]

object HListDeserializer {

  private type DS[A, AA] = Deserializer[A, AA] // alias for brevity

  implicit def fromDeserializer[L <: HList, T](ds: DS[L, T]) = new HListDeserializer[L, T] {
    def apply(list: L) = ds(list)
  }

  /////////////////////////////// CASE CLASS DESERIALIZATION ////////////////////////////////

  // we use a special exception to bubble up errors rather than relying on long "right.flatMap" cascades in order to
  // save lines of code as well as excessive closure class creation in the many "hld" methods below
  private class BubbleLeftException(val left: Left[Any, Any]) extends RuntimeException

  private def create[L <: HList, T](deserialize: L => T) = new HListDeserializer[L, T] {
    def apply(list: L) = {
      try Right(deserialize(list))
      catch {
        case e: BubbleLeftException => e.left.asInstanceOf[Left[DeserializationError, T]]
        case e: IllegalArgumentException => Left(MalformedContent(e.getMessage, e))
      }
    }
  }

  private def get[T](either: Either[DeserializationError, T]): T = either match {
    case Right(x) => x
    case left: Left[_, _] => throw new BubbleLeftException(left)
  }

  implicit def hld1[Z, A, AA]
      (construct: AA => Z)
      (implicit qa: DS[A, AA]) =
    create[A :: HNil, Z] {
      case a :: HNil => construct(
        get(qa(a))
      )
    }

  implicit def hld2[Z, A, AA, B, BB]
      (construct: (AA, BB) => Z)
      (implicit qa: DS[A, AA], qb: DS[B, BB]) =
    create[A :: B :: HNil, Z] {
      case a :: b :: HNil => construct(
        get(qa(a)),
        get(qb(b))
      )
    }

  implicit def hld3[Z, A, AA, B, BB, C, CC]
      (construct: (AA, BB, CC) => Z)
      (implicit qa: DS[A, AA], qb: DS[B, BB], qc: DS[C, CC]) =
    create[A :: B :: C :: HNil, Z] {
      case a :: b :: c :: HNil => construct(
        get(qa(a)),
        get(qb(b)),
        get(qc(c))
      )
    }

  implicit def hld4[Z, A, AA, B, BB, C, CC, D, DD]
      (construct: (AA, BB, CC, DD) => Z)
      (implicit qa: DS[A, AA], qb: DS[B, BB], qc: DS[C, CC], qd: DS[D, DD]) =
    create[A :: B :: C :: D :: HNil, Z] {
      case a :: b :: c :: d :: HNil => construct(
        get(qa(a)),
        get(qb(b)),
        get(qc(c)),
        get(qd(d))
      )
    }

  implicit def hld5[Z, A, AA, B, BB, C, CC, D, DD, E, EE]
      (construct: (AA, BB, CC, DD, EE) => Z)
      (implicit qa: DS[A, AA], qb: DS[B, BB], qc: DS[C, CC], qd: DS[D, DD], qe: DS[E, EE]) =
    create[A :: B :: C :: D :: E :: HNil, Z] {
      case a :: b :: c :: d :: e :: HNil => construct(
        get(qa(a)),
        get(qb(b)),
        get(qc(c)),
        get(qd(d)),
        get(qe(e))
      )
    }

  implicit def hld6[Z, A, AA, B, BB, C, CC, D, DD, E, EE, F, FF]
      (construct: (AA, BB, CC, DD, EE, FF) => Z)
      (implicit qa: DS[A, AA], qb: DS[B, BB], qc: DS[C, CC], qd: DS[D, DD], qe: DS[E, EE], qf: DS[F, FF]) =
    create[A :: B :: C :: D :: E :: F :: HNil, Z] {
      case a :: b :: c :: d :: e :: f :: HNil => construct(
        get(qa(a)),
        get(qb(b)),
        get(qc(c)),
        get(qd(d)),
        get(qe(e)),
        get(qf(f))
      )
    }

  implicit def hld7[Z, A, AA, B, BB, C, CC, D, DD, E, EE, F, FF, G, GG]
      (construct: (AA, BB, CC, DD, EE, FF, GG) => Z)
      (implicit qa: DS[A, AA], qb: DS[B, BB], qc: DS[C, CC], qd: DS[D, DD], qe: DS[E, EE], qf: DS[F, FF],
       qg: DS[G, GG]) =
    create[A :: B :: C :: D :: E :: F :: G :: HNil, Z] {
      case a :: b :: c :: d :: e :: f :: g :: HNil => construct(
        get(qa(a)),
        get(qb(b)),
        get(qc(c)),
        get(qd(d)),
        get(qe(e)),
        get(qf(f)),
        get(qg(g))
      )
    }

  implicit def hld8[Z, A, AA, B, BB, C, CC, D, DD, E, EE, F, FF, G, GG, H, HH]
      (construct: (AA, BB, CC, DD, EE, FF, GG, HH) => Z)
      (implicit qa: DS[A, AA], qb: DS[B, BB], qc: DS[C, CC], qd: DS[D, DD], qe: DS[E, EE], qf: DS[F, FF], qg: DS[G, GG],
       qh: DS[H, HH]) =
    create[A :: B :: C :: D :: E :: F :: G :: H :: HNil, Z] {
      case a :: b :: c :: d :: e :: f :: g :: h :: HNil => construct(
        get(qa(a)),
        get(qb(b)),
        get(qc(c)),
        get(qd(d)),
        get(qe(e)),
        get(qf(f)),
        get(qg(g)),
        get(qh(h))
      )
    }

  implicit def hld9[Z, A, AA, B, BB, C, CC, D, DD, E, EE, F, FF, G, GG, H, HH, I, II]
      (construct: (AA, BB, CC, DD, EE, FF, GG, HH, II) => Z)
      (implicit qa: DS[A, AA], qb: DS[B, BB], qc: DS[C, CC], qd: DS[D, DD], qe: DS[E, EE], qf: DS[F, FF], qg: DS[G, GG],
       qh: DS[H, HH], qi: DS[I, II]) =
    create[A :: B :: C :: D :: E :: F :: G :: H :: I :: HNil, Z] {
      case a :: b :: c :: d :: e :: f :: g :: h :: i :: HNil => construct(
        get(qa(a)),
        get(qb(b)),
        get(qc(c)),
        get(qd(d)),
        get(qe(e)),
        get(qf(f)),
        get(qg(g)),
        get(qh(h)),
        get(qi(i))
      )
    }

  implicit def hld10[Z, A, AA, B, BB, C, CC, D, DD, E, EE, F, FF, G, GG, H, HH, I, II, J, JJ]
      (construct: (AA, BB, CC, DD, EE, FF, GG, HH, II, JJ) => Z)
      (implicit qa: DS[A, AA], qb: DS[B, BB], qc: DS[C, CC], qd: DS[D, DD], qe: DS[E, EE], qf: DS[F, FF], qg: DS[G, GG],
       qh: DS[H, HH], qi: DS[I, II], qj: DS[J, JJ]) =
    create[A :: B :: C :: D :: E :: F :: G :: H :: I :: J :: HNil, Z] {
      case a :: b :: c :: d :: e :: f :: g :: h :: i :: j :: HNil => construct(
        get(qa(a)),
        get(qb(b)),
        get(qc(c)),
        get(qd(d)),
        get(qe(e)),
        get(qf(f)),
        get(qg(g)),
        get(qh(h)),
        get(qi(i)),
        get(qj(j))
      )
    }

  implicit def hld11[Z, A, AA, B, BB, C, CC, D, DD, E, EE, F, FF, G, GG, H, HH, I, II, J, JJ, K, KK]
      (construct: (AA, BB, CC, DD, EE, FF, GG, HH, II, JJ, KK) => Z)
      (implicit qa: DS[A, AA], qb: DS[B, BB], qc: DS[C, CC], qd: DS[D, DD], qe: DS[E, EE], qf: DS[F, FF], qg: DS[G, GG],
       qh: DS[H, HH], qi: DS[I, II], qj: DS[J, JJ], qk: DS[K, KK]) =
    create[A :: B :: C :: D :: E :: F :: G :: H :: I :: J :: K :: HNil, Z] {
      case a :: b :: c :: d :: e :: f :: g :: h :: i :: j :: k :: HNil => construct(
        get(qa(a)),
        get(qb(b)),
        get(qc(c)),
        get(qd(d)),
        get(qe(e)),
        get(qf(f)),
        get(qg(g)),
        get(qh(h)),
        get(qi(i)),
        get(qj(j)),
        get(qk(k))
      )
    }

  implicit def hld12[Z, A, AA, B, BB, C, CC, D, DD, E, EE, F, FF, G, GG, H, HH, I, II, J, JJ, K, KK, L, LL]
      (construct: (AA, BB, CC, DD, EE, FF, GG, HH, II, JJ, KK, LL) => Z)
      (implicit qa: DS[A, AA], qb: DS[B, BB], qc: DS[C, CC], qd: DS[D, DD], qe: DS[E, EE], qf: DS[F, FF], qg: DS[G, GG],
       qh: DS[H, HH], qi: DS[I, II], qj: DS[J, JJ], qk: DS[K, KK], ql: DS[L, LL]) =
    create[A :: B :: C :: D :: E :: F :: G :: H :: I :: J :: K :: L :: HNil, Z] {
      case a :: b :: c :: d :: e :: f :: g :: h :: i :: j :: k :: l :: HNil => construct(
        get(qa(a)),
        get(qb(b)),
        get(qc(c)),
        get(qd(d)),
        get(qe(e)),
        get(qf(f)),
        get(qg(g)),
        get(qh(h)),
        get(qi(i)),
        get(qj(j)),
        get(qk(k)),
        get(ql(l))
      )
    }
  
  implicit def hld13[Z, A, AA, B, BB, C, CC, D, DD, E, EE, F, FF, G, GG, H, HH, I, II, J, JJ, K, KK, L, LL, M, MM]
      (construct: (AA, BB, CC, DD, EE, FF, GG, HH, II, JJ, KK, LL, MM) => Z)
      (implicit qa: DS[A, AA], qb: DS[B, BB], qc: DS[C, CC], qd: DS[D, DD], qe: DS[E, EE], qf: DS[F, FF], qg: DS[G, GG],
       qh: DS[H, HH], qi: DS[I, II], qj: DS[J, JJ], qk: DS[K, KK], ql: DS[L, LL], qm: DS[M, MM]) =
    create[A :: B :: C :: D :: E :: F :: G :: H :: I :: J :: K :: L :: M :: HNil, Z] {
      case a :: b :: c :: d :: e :: f :: g :: h :: i :: j :: k :: l :: m :: HNil => construct(
        get(qa(a)),
        get(qb(b)),
        get(qc(c)),
        get(qd(d)),
        get(qe(e)),
        get(qf(f)),
        get(qg(g)),
        get(qh(h)),
        get(qi(i)),
        get(qj(j)),
        get(qk(k)),
        get(ql(l)),
        get(qm(m))
      )
    }
  
  implicit def hld14[Z, A, AA, B, BB, C, CC, D, DD, E, EE, F, FF, G, GG, H, HH, I, II, J, JJ, K, KK, L, LL, M, MM, N, NN]
      (construct: (AA, BB, CC, DD, EE, FF, GG, HH, II, JJ, KK, LL, MM, NN) => Z)
      (implicit qa: DS[A, AA], qb: DS[B, BB], qc: DS[C, CC], qd: DS[D, DD], qe: DS[E, EE], qf: DS[F, FF], qg: DS[G, GG],
       qh: DS[H, HH], qi: DS[I, II], qj: DS[J, JJ], qk: DS[K, KK], ql: DS[L, LL], qm: DS[M, MM], qn: DS[N, NN]) =
    create[A :: B :: C :: D :: E :: F :: G :: H :: I :: J :: K :: L :: M :: N :: HNil, Z] {
      case a :: b :: c :: d :: e :: f :: g :: h :: i :: j :: k :: l :: m :: n :: HNil => construct(
        get(qa(a)),
        get(qb(b)),
        get(qc(c)),
        get(qd(d)),
        get(qe(e)),
        get(qf(f)),
        get(qg(g)),
        get(qh(h)),
        get(qi(i)),
        get(qj(j)),
        get(qk(k)),
        get(ql(l)),
        get(qm(m)),
        get(qn(n))
      )
    }
  
  implicit def hld15[Z, A, AA, B, BB, C, CC, D, DD, E, EE, F, FF, G, GG, H, HH, I, II, J, JJ, K, KK, L, LL, M, MM, N, NN, O, OO]
      (construct: (AA, BB, CC, DD, EE, FF, GG, HH, II, JJ, KK, LL, MM, NN, OO) => Z)
      (implicit qa: DS[A, AA], qb: DS[B, BB], qc: DS[C, CC], qd: DS[D, DD], qe: DS[E, EE], qf: DS[F, FF], qg: DS[G, GG],
       qh: DS[H, HH], qi: DS[I, II], qj: DS[J, JJ], qk: DS[K, KK], ql: DS[L, LL], qm: DS[M, MM], qn: DS[N, NN],
       qo: DS[O, OO]) =
    create[A :: B :: C :: D :: E :: F :: G :: H :: I :: J :: K :: L :: M :: N :: O :: HNil, Z] {
      case a :: b :: c :: d :: e :: f :: g :: h :: i :: j :: k :: l :: m :: n :: o :: HNil => construct(
        get(qa(a)),
        get(qb(b)),
        get(qc(c)),
        get(qd(d)),
        get(qe(e)),
        get(qf(f)),
        get(qg(g)),
        get(qh(h)),
        get(qi(i)),
        get(qj(j)),
        get(qk(k)),
        get(ql(l)),
        get(qm(m)),
        get(qn(n)),
        get(qo(o))
      )
    }
  
  implicit def hld16[Z, A, AA, B, BB, C, CC, D, DD, E, EE, F, FF, G, GG, H, HH, I, II, J, JJ, K, KK, L, LL, M, MM, N, NN, O, OO, P, PP]
      (construct: (AA, BB, CC, DD, EE, FF, GG, HH, II, JJ, KK, LL, MM, NN, OO, PP) => Z)
      (implicit qa: DS[A, AA], qb: DS[B, BB], qc: DS[C, CC], qd: DS[D, DD], qe: DS[E, EE], qf: DS[F, FF], qg: DS[G, GG],
       qh: DS[H, HH], qi: DS[I, II], qj: DS[J, JJ], qk: DS[K, KK], ql: DS[L, LL], qm: DS[M, MM], qn: DS[N, NN],
       qo: DS[O, OO], qp: DS[P, PP]) =
    create[A :: B :: C :: D :: E :: F :: G :: H :: I :: J :: K :: L :: M :: N :: O :: P :: HNil, Z] {
      case a :: b :: c :: d :: e :: f :: g :: h :: i :: j :: k :: l :: m :: n :: o :: p :: HNil => construct(
        get(qa(a)),
        get(qb(b)),
        get(qc(c)),
        get(qd(d)),
        get(qe(e)),
        get(qf(f)),
        get(qg(g)),
        get(qh(h)),
        get(qi(i)),
        get(qj(j)),
        get(qk(k)),
        get(ql(l)),
        get(qm(m)),
        get(qn(n)),
        get(qo(o)),
        get(qp(p))
      )
    }
  
  implicit def hld17[Z, A, AA, B, BB, C, CC, D, DD, E, EE, F, FF, G, GG, H, HH, I, II, J, JJ, K, KK, L, LL, M, MM, N, NN, O, OO, P, PP, Q, QQ]
      (construct: (AA, BB, CC, DD, EE, FF, GG, HH, II, JJ, KK, LL, MM, NN, OO, PP, QQ) => Z)
      (implicit qa: DS[A, AA], qb: DS[B, BB], qc: DS[C, CC], qd: DS[D, DD], qe: DS[E, EE], qf: DS[F, FF], qg: DS[G, GG],
       qh: DS[H, HH], qi: DS[I, II], qj: DS[J, JJ], qk: DS[K, KK], ql: DS[L, LL], qm: DS[M, MM], qn: DS[N, NN],
       qo: DS[O, OO], qp: DS[P, PP], qq: DS[Q, QQ]) =
    create[A :: B :: C :: D :: E :: F :: G :: H :: I :: J :: K :: L :: M :: N :: O :: P :: Q :: HNil, Z] {
      case a :: b :: c :: d :: e :: f :: g :: h :: i :: j :: k :: l :: m :: n :: o :: p :: q :: HNil => construct(
        get(qa(a)),
        get(qb(b)),
        get(qc(c)),
        get(qd(d)),
        get(qe(e)),
        get(qf(f)),
        get(qg(g)),
        get(qh(h)),
        get(qi(i)),
        get(qj(j)),
        get(qk(k)),
        get(ql(l)),
        get(qm(m)),
        get(qn(n)),
        get(qo(o)),
        get(qp(p)),
        get(qq(q))
      )
    }
  
  implicit def hld18[Z, A, AA, B, BB, C, CC, D, DD, E, EE, F, FF, G, GG, H, HH, I, II, J, JJ, K, KK, L, LL, M, MM, N, NN, O, OO, P, PP, Q, QQ, R, RR]
      (construct: (AA, BB, CC, DD, EE, FF, GG, HH, II, JJ, KK, LL, MM, NN, OO, PP, QQ, RR) => Z)
      (implicit qa: DS[A, AA], qb: DS[B, BB], qc: DS[C, CC], qd: DS[D, DD], qe: DS[E, EE], qf: DS[F, FF], qg: DS[G, GG],
       qh: DS[H, HH], qi: DS[I, II], qj: DS[J, JJ], qk: DS[K, KK], ql: DS[L, LL], qm: DS[M, MM], qn: DS[N, NN],
       qo: DS[O, OO], qp: DS[P, PP], qq: DS[Q, QQ], qr: DS[R, RR]) =
    create[A :: B :: C :: D :: E :: F :: G :: H :: I :: J :: K :: L :: M :: N :: O :: P :: Q :: R :: HNil, Z] {
      case a :: b :: c :: d :: e :: f :: g :: h :: i :: j :: k :: l :: m :: n :: o :: p :: q :: r :: HNil => construct(
        get(qa(a)),
        get(qb(b)),
        get(qc(c)),
        get(qd(d)),
        get(qe(e)),
        get(qf(f)),
        get(qg(g)),
        get(qh(h)),
        get(qi(i)),
        get(qj(j)),
        get(qk(k)),
        get(ql(l)),
        get(qm(m)),
        get(qn(n)),
        get(qo(o)),
        get(qp(p)),
        get(qq(q)),
        get(qr(r))
      )
    }
  
  implicit def hld19[Z, A, AA, B, BB, C, CC, D, DD, E, EE, F, FF, G, GG, H, HH, I, II, J, JJ, K, KK, L, LL, M, MM, N, NN, O, OO, P, PP, Q, QQ, R, RR, S, SS]
      (construct: (AA, BB, CC, DD, EE, FF, GG, HH, II, JJ, KK, LL, MM, NN, OO, PP, QQ, RR, SS) => Z)
      (implicit qa: DS[A, AA], qb: DS[B, BB], qc: DS[C, CC], qd: DS[D, DD], qe: DS[E, EE], qf: DS[F, FF], qg: DS[G, GG],
       qh: DS[H, HH], qi: DS[I, II], qj: DS[J, JJ], qk: DS[K, KK], ql: DS[L, LL], qm: DS[M, MM], qn: DS[N, NN],
       qo: DS[O, OO], qp: DS[P, PP], qq: DS[Q, QQ], qr: DS[R, RR], qs: DS[S, SS]) =
    create[A :: B :: C :: D :: E :: F :: G :: H :: I :: J :: K :: L :: M :: N :: O :: P :: Q :: R :: S :: HNil, Z] {
      case a :: b :: c :: d :: e :: f :: g :: h :: i :: j :: k :: l :: m :: n :: o :: p :: q :: r :: s :: HNil => construct(
        get(qa(a)),
        get(qb(b)),
        get(qc(c)),
        get(qd(d)),
        get(qe(e)),
        get(qf(f)),
        get(qg(g)),
        get(qh(h)),
        get(qi(i)),
        get(qj(j)),
        get(qk(k)),
        get(ql(l)),
        get(qm(m)),
        get(qn(n)),
        get(qo(o)),
        get(qp(p)),
        get(qq(q)),
        get(qr(r)),
        get(qs(s))
      )
    }

  implicit def hld20[Z, A, AA, B, BB, C, CC, D, DD, E, EE, F, FF, G, GG, H, HH, I, II, J, JJ, K, KK, L, LL, M, MM, N, NN, O, OO, P, PP, Q, QQ, R, RR, S, SS, T, TT]
      (construct: (AA, BB, CC, DD, EE, FF, GG, HH, II, JJ, KK, LL, MM, NN, OO, PP, QQ, RR, SS, TT) => Z)
      (implicit qa: DS[A, AA], qb: DS[B, BB], qc: DS[C, CC], qd: DS[D, DD], qe: DS[E, EE], qf: DS[F, FF], qg: DS[G, GG],
       qh: DS[H, HH], qi: DS[I, II], qj: DS[J, JJ], qk: DS[K, KK], ql: DS[L, LL], qm: DS[M, MM], qn: DS[N, NN],
       qo: DS[O, OO], qp: DS[P, PP], qq: DS[Q, QQ], qr: DS[R, RR], qs: DS[S, SS], qt: DS[T, TT]) =
    create[A :: B :: C :: D :: E :: F :: G :: H :: I :: J :: K :: L :: M :: N :: O :: P :: Q :: R :: S :: T :: HNil, Z] {
      case a :: b :: c :: d :: e :: f :: g :: h :: i :: j :: k :: l :: m :: n :: o :: p :: q :: r :: s :: t :: HNil => construct(
        get(qa(a)),
        get(qb(b)),
        get(qc(c)),
        get(qd(d)),
        get(qe(e)),
        get(qf(f)),
        get(qg(g)),
        get(qh(h)),
        get(qi(i)),
        get(qj(j)),
        get(qk(k)),
        get(ql(l)),
        get(qm(m)),
        get(qn(n)),
        get(qo(o)),
        get(qp(p)),
        get(qq(q)),
        get(qr(r)),
        get(qs(s)),
        get(qt(t))
      )
    }

  implicit def hld21[Z, A, AA, B, BB, C, CC, D, DD, E, EE, F, FF, G, GG, H, HH, I, II, J, JJ, K, KK, L, LL, M, MM, N, NN, O, OO, P, PP, Q, QQ, R, RR, S, SS, T, TT, U, UU]
      (construct: (AA, BB, CC, DD, EE, FF, GG, HH, II, JJ, KK, LL, MM, NN, OO, PP, QQ, RR, SS, TT, UU) => Z)
      (implicit qa: DS[A, AA], qb: DS[B, BB], qc: DS[C, CC], qd: DS[D, DD], qe: DS[E, EE], qf: DS[F, FF], qg: DS[G, GG],
       qh: DS[H, HH], qi: DS[I, II], qj: DS[J, JJ], qk: DS[K, KK], ql: DS[L, LL], qm: DS[M, MM], qn: DS[N, NN],
       qo: DS[O, OO], qp: DS[P, PP], qq: DS[Q, QQ], qr: DS[R, RR], qs: DS[S, SS], qt: DS[T, TT], qu: DS[U, UU]) =
    create[A :: B :: C :: D :: E :: F :: G :: H :: I :: J :: K :: L :: M :: N :: O :: P :: Q :: R :: S :: T :: U :: HNil, Z] {
      case a :: b :: c :: d :: e :: f :: g :: h :: i :: j :: k :: l :: m :: n :: o :: p :: q :: r :: s :: t :: u :: HNil => construct(
        get(qa(a)),
        get(qb(b)),
        get(qc(c)),
        get(qd(d)),
        get(qe(e)),
        get(qf(f)),
        get(qg(g)),
        get(qh(h)),
        get(qi(i)),
        get(qj(j)),
        get(qk(k)),
        get(ql(l)),
        get(qm(m)),
        get(qn(n)),
        get(qo(o)),
        get(qp(p)),
        get(qq(q)),
        get(qr(r)),
        get(qs(s)),
        get(qt(t)),
        get(qu(u))
      )
    }

  implicit def hld22[Z, A, AA, B, BB, C, CC, D, DD, E, EE, F, FF, G, GG, H, HH, I, II, J, JJ, K, KK, L, LL, M, MM, N, NN, O, OO, P, PP, Q, QQ, R, RR, S, SS, T, TT, U, UU, V, VV]
      (construct: (AA, BB, CC, DD, EE, FF, GG, HH, II, JJ, KK, LL, MM, NN, OO, PP, QQ, RR, SS, TT, UU, VV) => Z)
      (implicit qa: DS[A, AA], qb: DS[B, BB], qc: DS[C, CC], qd: DS[D, DD], qe: DS[E, EE], qf: DS[F, FF], qg: DS[G, GG],
       qh: DS[H, HH], qi: DS[I, II], qj: DS[J, JJ], qk: DS[K, KK], ql: DS[L, LL], qm: DS[M, MM], qn: DS[N, NN],
       qo: DS[O, OO], qp: DS[P, PP], qq: DS[Q, QQ], qr: DS[R, RR], qs: DS[S, SS], qt: DS[T, TT], qu: DS[U, UU],
       qv: DS[V, VV]) =
    create[A :: B :: C :: D :: E :: F :: G :: H :: I :: J :: K :: L :: M :: N :: O :: P :: Q :: R :: S :: T :: U :: V :: HNil, Z] {
      case a :: b :: c :: d :: e :: f :: g :: h :: i :: j :: k :: l :: m :: n :: o :: p :: q :: r :: s :: t :: u :: v :: HNil => construct(
        get(qa(a)),
        get(qb(b)),
        get(qc(c)),
        get(qd(d)),
        get(qe(e)),
        get(qf(f)),
        get(qg(g)),
        get(qh(h)),
        get(qi(i)),
        get(qj(j)),
        get(qk(k)),
        get(ql(l)),
        get(qm(m)),
        get(qn(n)),
        get(qo(o)),
        get(qp(p)),
        get(qq(q)),
        get(qr(r)),
        get(qs(s)),
        get(qt(t)),
        get(qu(u)),
        get(qv(v))
      )
    }

}