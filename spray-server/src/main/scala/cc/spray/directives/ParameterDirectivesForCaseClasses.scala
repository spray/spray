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

private[spray] trait ParameterDirectivesForCaseClasses {
  this: BasicDirectives with ParameterDirectives =>

  private type SP[A] = SimpleParser[A]

  implicit def pimpSprayRoute1[A](route: SprayRoute1[A]) = new PimpedSprayRoute1(route)
  class PimpedSprayRoute1[A](route: SprayRoute1[A]) extends SprayRoute1[A](route.filter) {
    def as[T <: Product](converter: A => Either[String, T]): SprayRoute1[T] = filter1 { ctx =>
      route.filter(ctx) match {
        case Pass(Tuple1(a), transform) => converter(a) match {
          case Right(t) => Pass.withTransform(t)(transform)
          case Left(msg) => Reject(MalformedQueryParamRejection(msg))
        }
        case x: Reject => x
      }
    }
  }

  implicit def pimpSprayRoute2[A, B](route: SprayRoute2[A, B]) = new PimpedSprayRoute2(route)
  class PimpedSprayRoute2[A, B](route: SprayRoute2[A, B]) extends SprayRoute2[A, B](route.filter) {
    def as[T <: Product](converter: (A, B) => Either[String, T]): SprayRoute1[T] = filter1 { ctx =>
      route.filter(ctx) match {
        case Pass((a, b), transform) => converter(a, b) match {
          case Right(t) => Pass.withTransform(t)(transform)
          case Left(msg) => Reject(MalformedQueryParamRejection(msg))
        }
        case x: Reject => x
      }
    }
  }

  implicit def pimpSprayRoute3[A, B, C](route: SprayRoute3[A, B, C]) = new PimpedSprayRoute3(route)
  class PimpedSprayRoute3[A, B, C](route: SprayRoute3[A, B, C]) extends SprayRoute3[A, B, C](route.filter) {
    def as[T <: Product](converter: (A, B, C) => Either[String, T]): SprayRoute1[T] = filter1 { ctx =>
      route.filter(ctx) match {
        case Pass((a, b, c), transform) => converter(a, b, c) match {
          case Right(t) => Pass.withTransform(t)(transform)
          case Left(msg) => Reject(MalformedQueryParamRejection(msg))
        }
        case x: Reject => x
      }
    }
  }

  implicit def pimpSprayRoute4[A, B, C, D](route: SprayRoute4[A, B, C, D]) = new PimpedSprayRoute4(route)
  class PimpedSprayRoute4[A, B, C, D](route: SprayRoute4[A, B, C, D]) extends SprayRoute4[A, B, C, D](route.filter) {
    def as[T <: Product](converter: (A, B, C, D) => Either[String, T]): SprayRoute1[T] = filter1 { ctx =>
      route.filter(ctx) match {
        case Pass((a, b, c, d), transform) => converter(a, b, c, d) match {
          case Right(t) => Pass.withTransform(t)(transform)
          case Left(msg) => Reject(MalformedQueryParamRejection(msg))
        }
        case x: Reject => x
      }
    }
  }

  implicit def pimpSprayRoute5[A, B, C, D, E](route: SprayRoute5[A, B, C, D, E]) = new PimpedSprayRoute5(route)
  class PimpedSprayRoute5[A, B, C, D, E](route: SprayRoute5[A, B, C, D, E]) extends SprayRoute5[A, B, C, D, E](route.filter) {
    def as[T <: Product](converter: (A, B, C, D, E) => Either[String, T]): SprayRoute1[T] = filter1 { ctx =>
      route.filter(ctx) match {
        case Pass((a, b, c, d, e), transform) => converter(a, b, c, d, e) match {
          case Right(t) => Pass.withTransform(t)(transform)
          case Left(msg) => Reject(MalformedQueryParamRejection(msg))
        }
        case x: Reject => x
      }
    }
  }

  implicit def pimpSprayRoute6[A, B, C, D, E, F](route: SprayRoute6[A, B, C, D, E, F]) = new PimpedSprayRoute6(route)
  class PimpedSprayRoute6[A, B, C, D, E, F](route: SprayRoute6[A, B, C, D, E, F]) extends SprayRoute6[A, B, C, D, E, F](route.filter) {
    def as[T <: Product](converter: (A, B, C, D, E, F) => Either[String, T]): SprayRoute1[T] = filter1 { ctx =>
      route.filter(ctx) match {
        case Pass((a, b, c, d, e, f), transform) => converter(a, b, c, d, e, f) match {
          case Right(t) => Pass.withTransform(t)(transform)
          case Left(msg) => Reject(MalformedQueryParamRejection(msg))
        }
        case x: Reject => x
      }
    }
  }

  implicit def pimpSprayRoute7[A, B, C, D, E, F, G](route: SprayRoute7[A, B, C, D, E, F, G]) = new PimpedSprayRoute7(route)
  class PimpedSprayRoute7[A, B, C, D, E, F, G](route: SprayRoute7[A, B, C, D, E, F, G]) extends SprayRoute7[A, B, C, D, E, F, G](route.filter) {
    def as[T <: Product](converter: (A, B, C, D, E, F, G) => Either[String, T]): SprayRoute1[T] = filter1 { ctx =>
      route.filter(ctx) match {
        case Pass((a, b, c, d, e, f, g), transform) => converter(a, b, c, d, e, f, g) match {
          case Right(t) => Pass.withTransform(t)(transform)
          case Left(msg) => Reject(MalformedQueryParamRejection(msg))
        }
        case x: Reject => x
      }
    }
  }

  def instanceOf[T <: Product, A: SP](construct: A => T): String => Either[String, T] = { as =>
    simpleParser[A].apply(as).right.flatMap { a =>
      try {
        Right(construct(a))
      } catch {
        case e: IllegalArgumentException => Left(e.getMessage)
      }
    }
  }

  def instanceOf[T <: Product, A: SP, B: SP](construct: (A, B) => T): (String, String) => Either[String, T] = { (as, bs) =>
    simpleParser[A].apply(as).right.flatMap { a =>
      simpleParser[B].apply(bs).right.flatMap { b =>
        try {
          Right(construct(a, b))
        } catch {
          case e: IllegalArgumentException => Left(e.getMessage)
        }
      }
    }
  }

  def instanceOf[T <: Product, A: SP, B: SP, C: SP](construct: (A, B, C) => T): (String, String, String) => Either[String, T] = { (as, bs, cs) =>
    simpleParser[A].apply(as).right.flatMap { a =>
      simpleParser[B].apply(bs).right.flatMap { b =>
        simpleParser[C].apply(cs).right.flatMap { c =>
          try {
            Right(construct(a, b, c))
          } catch {
            case e: IllegalArgumentException => Left(e.getMessage)
          }
        }
      }
    }
  }

  def instanceOf[T <: Product, A: SP, B: SP, C: SP, D: SP](construct: (A, B, C, D) => T): (String, String, String, String) => Either[String, T] = { (as, bs, cs, ds) =>
    simpleParser[A].apply(as).right.flatMap { a =>
      simpleParser[B].apply(bs).right.flatMap { b =>
        simpleParser[C].apply(cs).right.flatMap { c =>
          simpleParser[D].apply(ds).right.flatMap { d =>
            try {
              Right(construct(a, b, c, d))
            } catch {
              case e: IllegalArgumentException => Left(e.getMessage)
            }
          }
        }
      }
    }
  }

  def instanceOf[T <: Product, A: SP, B: SP, C: SP, D: SP, E: SP](construct: (A, B, C, D, E) => T): (String, String, String, String, String) => Either[String, T] = { (as, bs, cs, ds, es) =>
    simpleParser[A].apply(as).right.flatMap { a =>
      simpleParser[B].apply(bs).right.flatMap { b =>
        simpleParser[C].apply(cs).right.flatMap { c =>
          simpleParser[D].apply(ds).right.flatMap { d =>
            simpleParser[E].apply(es).right.flatMap { e =>
              try {
                Right(construct(a, b, c, d, e))
              } catch {
                case e: IllegalArgumentException => Left(e.getMessage)
              }
            }
          }
        }
      }
    }
  }

  def instanceOf[T <: Product, A: SP, B: SP, C: SP, D: SP, E: SP, F: SP](construct: (A, B, C, D, E, F) => T): (String, String, String, String, String, String) => Either[String, T] = { (as, bs, cs, ds, es, fs) =>
    simpleParser[A].apply(as).right.flatMap { a =>
      simpleParser[B].apply(bs).right.flatMap { b =>
        simpleParser[C].apply(cs).right.flatMap { c =>
          simpleParser[D].apply(ds).right.flatMap { d =>
            simpleParser[E].apply(es).right.flatMap { e =>
              simpleParser[F].apply(fs).right.flatMap { f =>
                try {
                  Right(construct(a, b, c, d, e, f))
                } catch {
                  case e: IllegalArgumentException => Left(e.getMessage)
                }
              }
            }
          }
        }
      }
    }
  }

  def instanceOf[T <: Product, A: SP, B: SP, C: SP, D: SP, E: SP, F: SP, G: SP](construct: (A, B, C, D, E, F, G) => T): (String, String, String, String, String, String, String) => Either[String, T] = { (as, bs, cs, ds, es, fs, gs) =>
    simpleParser[A].apply(as).right.flatMap { a =>
      simpleParser[B].apply(bs).right.flatMap { b =>
        simpleParser[C].apply(cs).right.flatMap { c =>
          simpleParser[D].apply(ds).right.flatMap { d =>
            simpleParser[E].apply(es).right.flatMap { e =>
              simpleParser[F].apply(fs).right.flatMap { f =>
                simpleParser[G].apply(gs).right.flatMap { g =>
                  try {
                    Right(construct(a, b, c, d, e, f, g))
                  } catch {
                    case e: IllegalArgumentException => Left(e.getMessage)
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
