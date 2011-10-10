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
package directives

import typeconversion._
import scala.Either

private[spray] trait CaseClassExtractionDirectives {
  this: BasicDirectives =>

  implicit def pimpSprayRoute1[A](route: SprayRoute1[A]) = new PimpedSprayRoute1(route)
  class PimpedSprayRoute1[A](route: SprayRoute1[A]) extends SprayRoute1[A](route.filter) {
    def as[T <: Product](converter: A => Either[DeserializationError, T]) = convert(route) {
      case Tuple1(a) => converter(a)
    }
  }

  implicit def pimpSprayRoute2[A, B](route: SprayRoute2[A, B]) = new PimpedSprayRoute2(route)
  class PimpedSprayRoute2[A, B](route: SprayRoute2[A, B]) extends SprayRoute2[A, B](route.filter) {
    def as[T <: Product](converter: (A, B) => Either[DeserializationError, T]) = convert(route) {
      case (a, b) => converter(a, b)
    }
  }

  implicit def pimpSprayRoute3[A, B, C](route: SprayRoute3[A, B, C]) = new PimpedSprayRoute3(route)
  class PimpedSprayRoute3[A, B, C](route: SprayRoute3[A, B, C]) extends SprayRoute3[A, B, C](route.filter) {
    def as[T <: Product](converter: (A, B, C) => Either[DeserializationError, T]) = convert(route) {
      case (a, b, c) => converter(a, b, c)
    }
  }

  implicit def pimpSprayRoute4[A, B, C, D](route: SprayRoute4[A, B, C, D]) = new PimpedSprayRoute4(route)
  class PimpedSprayRoute4[A, B, C, D](route: SprayRoute4[A, B, C, D]) extends SprayRoute4[A, B, C, D](route.filter) {
    def as[T <: Product](converter: (A, B, C, D) => Either[DeserializationError, T]) = convert(route) {
      case (a, b, c, d) => converter(a, b, c, d)
    }
  }

  implicit def pimpSprayRoute5[A, B, C, D, E](route: SprayRoute5[A, B, C, D, E]) = new PimpedSprayRoute5(route)
  class PimpedSprayRoute5[A, B, C, D, E](route: SprayRoute5[A, B, C, D, E]) extends SprayRoute5[A, B, C, D, E](route.filter) {
    def as[T <: Product](converter: (A, B, C, D, E) => Either[DeserializationError, T]) = convert(route) {
      case (a, b, c, d, e) => converter(a, b, c, d, e)
    }
  }

  implicit def pimpSprayRoute6[A, B, C, D, E, F](route: SprayRoute6[A, B, C, D, E, F]) = new PimpedSprayRoute6(route)
  class PimpedSprayRoute6[A, B, C, D, E, F](route: SprayRoute6[A, B, C, D, E, F]) extends SprayRoute6[A, B, C, D, E, F](route.filter) {
    def as[T <: Product](converter: (A, B, C, D, E, F) => Either[DeserializationError, T]) = convert(route) {
      case (a, b, c, d, e, f) => converter(a, b, c, d, e, f)
    }
  }

  implicit def pimpSprayRoute7[A, B, C, D, E, F, G](route: SprayRoute7[A, B, C, D, E, F, G]) = new PimpedSprayRoute7(route)
  class PimpedSprayRoute7[A, B, C, D, E, F, G](route: SprayRoute7[A, B, C, D, E, F, G]) extends SprayRoute7[A, B, C, D, E, F, G](route.filter) {
    def as[T <: Product](converter: (A, B, C, D, E, F, G) => Either[DeserializationError, T]) = convert(route) {
      case (a, b, c, d, e, f, g) => converter(a, b, c, d, e, f, g)
    }
  }

  implicit def pimpSprayRoute8[A, B, C, D, E, F, G, H](route: SprayRoute8[A, B, C, D, E, F, G, H]) = new PimpedSprayRoute8(route)
  class PimpedSprayRoute8[A, B, C, D, E, F, G, H](route: SprayRoute8[A, B, C, D, E, F, G, H]) extends SprayRoute8[A, B, C, D, E, F, G, H](route.filter) {
    def as[T <: Product](converter: (A, B, C, D, E, F, G, H) => Either[DeserializationError, T]) = convert(route) {
      case (a, b, c, d, e, f, g, h) => converter(a, b, c, d, e, f, g, h)
    }
  }

  implicit def pimpSprayRoute9[A, B, C, D, E, F, G, H, I](route: SprayRoute9[A, B, C, D, E, F, G, H, I]) = new PimpedSprayRoute9(route)
  class PimpedSprayRoute9[A, B, C, D, E, F, G, H, I](route: SprayRoute9[A, B, C, D, E, F, G, H, I]) extends SprayRoute9[A, B, C, D, E, F, G, H, I](route.filter) {
    def as[T <: Product](converter: (A, B, C, D, E, F, G, H, I) => Either[DeserializationError, T]) = convert(route) {
      case (a, b, c, d, e, f, g, h, i) => converter(a, b, c, d, e, f, g, h, i)
    }
  }

  private def convert[P <: Product, T <: Product](route: SprayRoute[P])
                                                 (converter: P => Either[DeserializationError, T]): SprayRoute1[T] = {
    filter1 { ctx =>
      route.filter(ctx) match {
        case Pass(values, transform) => converter(values) match {
          case Right(t) => Pass.withTransform(t)(transform)
          case Left(MalformedContent(msg)) => Reject(ValidationRejection(msg))
          case Left(error) => Reject(ValidationRejection(error.toString))
        }
        case x: Reject => x
      }
    }
  }

  private type TC[AA, A] = Deserializer[AA, A]

  // TODO: refactor to prevent excessive closure class creation in the following 9 methods

  def instanceOf[T <: Product, A, AA](construct: A => T)
                                     (implicit qa: TC[AA, A])
    : AA => Either[DeserializationError, T] = { aa =>
    qa(aa).right.flatMap { a =>
      protect(construct(a))
    }
  }

  def instanceOf[T <: Product, A, AA, B, BB](construct: (A, B) => T)
                                            (implicit qa: TC[AA, A], qb: TC[BB, B])
    : (AA, BB) => Either[DeserializationError, T] = { (aa, bb) =>
    qa(aa).right.flatMap { a =>
      qb(bb).right.flatMap { b =>
        protect(construct(a, b))
      }
    }
  }

  def instanceOf[T <: Product, A, AA, B, BB, C, CC](construct: (A, B, C) => T)
                                                   (implicit qa: TC[AA, A], qb: TC[BB, B], qc: TC[CC, C])
    : (AA, BB, CC) => Either[DeserializationError, T] = { (aa, bb, cc) =>
    qa(aa).right.flatMap { a =>
      qb(bb).right.flatMap { b =>
        qc(cc).right.flatMap { c =>
          protect(construct(a, b, c))
        }
      }
    }
  }

  def instanceOf[T <: Product, A, AA, B, BB, C, CC, D, DD](construct: (A, B, C, D) => T)
                                                          (implicit qa: TC[AA, A], qb: TC[BB, B], qc: TC[CC, C], qd: TC[DD, D])
    : (AA, BB, CC, DD) => Either[DeserializationError, T] = { (aa, bb, cc, dd) =>
    qa(aa).right.flatMap { a =>
      qb(bb).right.flatMap { b =>
        qc(cc).right.flatMap { c =>
          qd(dd).right.flatMap { d =>
            protect(construct(a, b, c, d))
          }
        }
      }
    }
  }

  def instanceOf[T <: Product, A, AA, B, BB, C, CC, D, DD, E, EE](construct: (A, B, C, D, E) => T)
                                                                 (implicit qa: TC[AA, A], qb: TC[BB, B], qc: TC[CC, C], qd: TC[DD, D], qe: TC[EE, E])
    : (AA, BB, CC, DD, EE) => Either[DeserializationError, T] = { (aa, bb, cc, dd, ee) =>
    qa(aa).right.flatMap { a =>
      qb(bb).right.flatMap { b =>
        qc(cc).right.flatMap { c =>
          qd(dd).right.flatMap { d =>
            qe(ee).right.flatMap { e =>
              protect(construct(a, b, c, d, e))
            }
          }
        }
      }
    }
  }

  def instanceOf[T <: Product, A, AA, B, BB, C, CC, D, DD, E, EE, F, FF](construct: (A, B, C, D, E, F) => T)
                                                                        (implicit qa: TC[AA, A], qb: TC[BB, B], qc: TC[CC, C], qd: TC[DD, D], qe: TC[EE, E], qf: TC[FF, F])
    : (AA, BB, CC, DD, EE, FF) => Either[DeserializationError, T] = { (aa, bb, cc, dd, ee, ff) =>
    qa(aa).right.flatMap { a =>
      qb(bb).right.flatMap { b =>
        qc(cc).right.flatMap { c =>
          qd(dd).right.flatMap { d =>
            qe(ee).right.flatMap { e =>
              qf(ff).right.flatMap { f =>
                protect(construct(a, b, c, d, e, f))
              }
            }
          }
        }
      }
    }
  }

  def instanceOf[T <: Product, A, AA, B, BB, C, CC, D, DD, E, EE, F, FF, G, GG](construct: (A, B, C, D, E, F, G) => T)
                                                                               (implicit qa: TC[AA, A], qb: TC[BB, B], qc: TC[CC, C], qd: TC[DD, D], qe: TC[EE, E], qf: TC[FF, F], qg: TC[GG, G])
    : (AA, BB, CC, DD, EE, FF, GG) => Either[DeserializationError, T] = { (aa, bb, cc, dd, ee, ff, gg) =>
    qa(aa).right.flatMap { a =>
      qb(bb).right.flatMap { b =>
        qc(cc).right.flatMap { c =>
          qd(dd).right.flatMap { d =>
            qe(ee).right.flatMap { e =>
              qf(ff).right.flatMap { f =>
                qg(gg).right.flatMap { g =>
                  protect(construct(a, b, c, d, e, f, g))
                }
              }
            }
          }
        }
      }
    }
  }

  def instanceOf[T <: Product, A, AA, B, BB, C, CC, D, DD, E, EE, F, FF, G, GG, H, HH](construct: (A, B, C, D, E, F, G, H) => T)
                                                                               (implicit qa: TC[AA, A], qb: TC[BB, B], qc: TC[CC, C], qd: TC[DD, D], qe: TC[EE, E], qf: TC[FF, F], qg: TC[GG, G], qh: TC[HH, H])
    : (AA, BB, CC, DD, EE, FF, GG, HH) => Either[DeserializationError, T] = { (aa, bb, cc, dd, ee, ff, gg, hh) =>
    qa(aa).right.flatMap { a =>
      qb(bb).right.flatMap { b =>
        qc(cc).right.flatMap { c =>
          qd(dd).right.flatMap { d =>
            qe(ee).right.flatMap { e =>
              qf(ff).right.flatMap { f =>
                qg(gg).right.flatMap { g =>
                  qh(hh).right.flatMap { h =>
                    protect(construct(a, b, c, d, e, f, g, h))
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  def instanceOf[T <: Product, A, AA, B, BB, C, CC, D, DD, E, EE, F, FF, G, GG, H, HH, I, II](construct: (A, B, C, D, E, F, G, H, I) => T)
                                                                               (implicit qa: TC[AA, A], qb: TC[BB, B], qc: TC[CC, C], qd: TC[DD, D], qe: TC[EE, E], qf: TC[FF, F], qg: TC[GG, G], qh: TC[HH, H], qi: TC[II, I])
    : (AA, BB, CC, DD, EE, FF, GG, HH, II) => Either[DeserializationError, T] = { (aa, bb, cc, dd, ee, ff, gg, hh, ii) =>
    qa(aa).right.flatMap { a =>
      qb(bb).right.flatMap { b =>
        qc(cc).right.flatMap { c =>
          qd(dd).right.flatMap { d =>
            qe(ee).right.flatMap { e =>
              qf(ff).right.flatMap { f =>
                qg(gg).right.flatMap { g =>
                  qh(hh).right.flatMap { h =>
                    qi(ii).right.flatMap { i =>
                      protect(construct(a, b, c, d, e, f, g, h, i))
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  private def protect[T](f: => T): Either[DeserializationError, T] = {
    try {
      Right(f)
    } catch {
      case e: IllegalArgumentException => Left(MalformedContent(e.getMessage))
    }
  }
}
