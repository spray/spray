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

  private type PC[A] = ParameterConverter[A]

  implicit def pimpSprayRoute1(route: SprayRoute1[String]) = new PimpedSprayRoute1(route)
  class PimpedSprayRoute1(route: SprayRoute1[String]) extends SprayRoute1[String](route.filter) {
    def as[T <: Product](converter: String => Either[String, T]): SprayRoute1[T] = filter1 { ctx =>
      route.filter(ctx) match {
        case Pass(Tuple1(a), transform) => converter(a) match {
          case Right(t) => Pass.withTransform(t)(transform)
          case Left(msg) => Reject(MalformedQueryParamRejection(msg))
        }
        case x: Reject => x
      }
    }
  }

  implicit def pimpSprayRoute2(route: SprayRoute2[String, String]) = new PimpedSprayRoute2(route)
  class PimpedSprayRoute2(route: SprayRoute2[String, String]) extends SprayRoute2[String, String](route.filter) {
    def as[T <: Product](converter: (String, String) => Either[String, T]): SprayRoute1[T] = filter1 { ctx =>
      route.filter(ctx) match {
        case Pass((a, b), transform) => converter(a, b) match {
          case Right(t) => Pass.withTransform(t)(transform)
          case Left(msg) => Reject(MalformedQueryParamRejection(msg))
        }
        case x: Reject => x
      }
    }
  }

  implicit def pimpSprayRoute3(route: SprayRoute3[String, String, String]) = new PimpedSprayRoute3(route)
  class PimpedSprayRoute3(route: SprayRoute3[String, String, String]) extends SprayRoute3[String, String, String](route.filter) {
    def as[T <: Product](converter: (String, String, String) => Either[String, T]): SprayRoute1[T] = filter1 { ctx =>
      route.filter(ctx) match {
        case Pass((a, b, c), transform) => converter(a, b, c) match {
          case Right(t) => Pass.withTransform(t)(transform)
          case Left(msg) => Reject(MalformedQueryParamRejection(msg))
        }
        case x: Reject => x
      }
    }
  }

  implicit def pimpSprayRoute4(route: SprayRoute4[String, String, String, String]) = new PimpedSprayRoute4(route)
  class PimpedSprayRoute4(route: SprayRoute4[String, String, String, String]) extends SprayRoute4[String, String, String, String](route.filter) {
    def as[T <: Product](converter: (String, String, String, String) => Either[String, T]): SprayRoute1[T] = filter1 { ctx =>
      route.filter(ctx) match {
        case Pass((a, b, c, d), transform) => converter(a, b, c, d) match {
          case Right(t) => Pass.withTransform(t)(transform)
          case Left(msg) => Reject(MalformedQueryParamRejection(msg))
        }
        case x: Reject => x
      }
    }
  }

  implicit def pimpSprayRoute5(route: SprayRoute5[String, String, String, String, String]) = new PimpedSprayRoute5(route)
  class PimpedSprayRoute5(route: SprayRoute5[String, String, String, String, String]) extends SprayRoute5[String, String, String, String, String](route.filter) {
    def as[T <: Product](converter: (String, String, String, String, String) => Either[String, T]): SprayRoute1[T] = filter1 { ctx =>
      route.filter(ctx) match {
        case Pass((a, b, c, d, e), transform) => converter(a, b, c, d, e) match {
          case Right(t) => Pass.withTransform(t)(transform)
          case Left(msg) => Reject(MalformedQueryParamRejection(msg))
        }
        case x: Reject => x
      }
    }
  }

  implicit def pimpSprayRoute6(route: SprayRoute6[String, String, String, String, String, String]) = new PimpedSprayRoute6(route)
  class PimpedSprayRoute6(route: SprayRoute6[String, String, String, String, String, String]) extends SprayRoute6[String, String, String, String, String, String](route.filter) {
    def as[T <: Product](converter: (String, String, String, String, String, String) => Either[String, T]): SprayRoute1[T] = filter1 { ctx =>
      route.filter(ctx) match {
        case Pass((a, b, c, d, e, f), transform) => converter(a, b, c, d, e, f) match {
          case Right(t) => Pass.withTransform(t)(transform)
          case Left(msg) => Reject(MalformedQueryParamRejection(msg))
        }
        case x: Reject => x
      }
    }
  }

  implicit def pimpSprayRoute7(route: SprayRoute7[String, String, String, String, String, String, String]) = new PimpedSprayRoute7(route)
  class PimpedSprayRoute7(route: SprayRoute7[String, String, String, String, String, String, String]) extends SprayRoute7[String, String, String, String, String, String, String](route.filter) {
    def as[T <: Product](converter: (String, String, String, String, String, String, String) => Either[String, T]): SprayRoute1[T] = filter1 { ctx =>
      route.filter(ctx) match {
        case Pass((a, b, c, d, e, f, g), transform) => converter(a, b, c, d, e, f, g) match {
          case Right(t) => Pass.withTransform(t)(transform)
          case Left(msg) => Reject(MalformedQueryParamRejection(msg))
        }
        case x: Reject => x
      }
    }
  }

  def instanceOf[T <: Product, A: PC](construct: A => T): String => Either[String, T] = { as =>
    parameterConverter[A].apply(as).right.flatMap { a =>
      try {
        Right(construct(a))
      } catch {
        case e: IllegalArgumentException => Left(e.getMessage)
      }
    }
  }

  def instanceOf[T <: Product, A: PC, B: PC](construct: (A, B) => T): (String, String) => Either[String, T] = { (as, bs) =>
    parameterConverter[A].apply(as).right.flatMap { a =>
      parameterConverter[B].apply(bs).right.flatMap { b =>
        try {
          Right(construct(a, b))
        } catch {
          case e: IllegalArgumentException => Left(e.getMessage)
        }
      }
    }
  }

  def instanceOf[T <: Product, A: PC, B: PC, C: PC](construct: (A, B, C) => T): (String, String, String) => Either[String, T] = { (as, bs, cs) =>
    parameterConverter[A].apply(as).right.flatMap { a =>
      parameterConverter[B].apply(bs).right.flatMap { b =>
        parameterConverter[C].apply(cs).right.flatMap { c =>
          try {
            Right(construct(a, b, c))
          } catch {
            case e: IllegalArgumentException => Left(e.getMessage)
          }
        }
      }
    }
  }

  def instanceOf[T <: Product, A: PC, B: PC, C: PC, D: PC](construct: (A, B, C, D) => T): (String, String, String, String) => Either[String, T] = { (as, bs, cs, ds) =>
    parameterConverter[A].apply(as).right.flatMap { a =>
      parameterConverter[B].apply(bs).right.flatMap { b =>
        parameterConverter[C].apply(cs).right.flatMap { c =>
          parameterConverter[D].apply(ds).right.flatMap { d =>
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

  def instanceOf[T <: Product, A: PC, B: PC, C: PC, D: PC, E: PC](construct: (A, B, C, D, E) => T): (String, String, String, String, String) => Either[String, T] = { (as, bs, cs, ds, es) =>
    parameterConverter[A].apply(as).right.flatMap { a =>
      parameterConverter[B].apply(bs).right.flatMap { b =>
        parameterConverter[C].apply(cs).right.flatMap { c =>
          parameterConverter[D].apply(ds).right.flatMap { d =>
            parameterConverter[E].apply(es).right.flatMap { e =>
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

  def instanceOf[T <: Product, A: PC, B: PC, C: PC, D: PC, E: PC, F: PC](construct: (A, B, C, D, E, F) => T): (String, String, String, String, String, String) => Either[String, T] = { (as, bs, cs, ds, es, fs) =>
    parameterConverter[A].apply(as).right.flatMap { a =>
      parameterConverter[B].apply(bs).right.flatMap { b =>
        parameterConverter[C].apply(cs).right.flatMap { c =>
          parameterConverter[D].apply(ds).right.flatMap { d =>
            parameterConverter[E].apply(es).right.flatMap { e =>
              parameterConverter[F].apply(fs).right.flatMap { f =>
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

  def instanceOf[T <: Product, A: PC, B: PC, C: PC, D: PC, E: PC, F: PC, G: PC](construct: (A, B, C, D, E, F, G) => T): (String, String, String, String, String, String, String) => Either[String, T] = { (as, bs, cs, ds, es, fs, gs) =>
    parameterConverter[A].apply(as).right.flatMap { a =>
      parameterConverter[B].apply(bs).right.flatMap { b =>
        parameterConverter[C].apply(cs).right.flatMap { c =>
          parameterConverter[D].apply(ds).right.flatMap { d =>
            parameterConverter[E].apply(es).right.flatMap { e =>
              parameterConverter[F].apply(fs).right.flatMap { f =>
                parameterConverter[G].apply(gs).right.flatMap { g =>
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
