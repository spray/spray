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
package directives

import java.lang.IllegalStateException
import spray.http.QueryParams
import shapeless._


trait ParameterDirectives extends ToNameReceptaclePimps {

  /**
   * Extracts the requests query parameters as a Map[String, String].
   */
  def parameterMap: Directive[QueryParams :: HNil] =
    BasicDirectives.filter { ctx => Pass(ctx.request.queryParams :: HNil) }

  /**
   * Rejects the request if the query parameter matcher(s) defined by the definition(s) don't match.
   * Otherwise the parameter value(s) are extracted and passed to the inner route.
   */
  def parameter(pdm: ParamDefMagnet): pdm.Out = pdm()

  /**
   * Rejects the request if the query parameter matcher(s) defined by the definition(s) don't match.
   * Otherwise the parameter value(s) are extracted and passed to the inner route.
   */
  def parameters(pdm: ParamDefMagnet): pdm.Out = pdm()

}

object ParameterDirectives extends ParameterDirectives


trait ParamDefMagnet {
  type Out
  def apply(): Out
}
object ParamDefMagnet {
  implicit def apply[T](value: T)(implicit pdm2: ParamDefMagnet2[T]) = new ParamDefMagnet {
    type Out = pdm2.Out
    def apply() = pdm2(value)
  }
}


trait ParamDefMagnet2[T] {
  type Out
  def apply(value: T): Out
}
object ParamDefMagnet2 {
  implicit def apply[A, B](implicit pdma: ParamDefMagnetAux[A, B]) = new ParamDefMagnet2[A] {
    type Out = B
    def apply(value: A) = pdma(value)
  }
}


trait ParamDefMagnetAux[A, B] extends (A => B)

object ParamDefMagnetAux {
  import spray.httpx.unmarshalling.{FromStringOptionDeserializer => FSOD, _}

  def apply[A, B](f: A => B) = new ParamDefMagnetAux[A, B] { def apply(value: A) = f(value) }


  /************ "regular" parameter extraction ******************/

  private def extractParameter[A, B](f: A => Directive[B :: HNil]) = ParamDefMagnetAux[A, Directive[B :: HNil]](f)
  private def filter[T](paramName: String, fsod: FSOD[T]) = BasicDirectives.filter { ctx =>
    fsod(ctx.request.queryParams.get(paramName)) match {
      case Right(x) => Pass(x :: HNil)
      case Left(ContentExpected) => Reject(MissingQueryParamRejection(paramName))
      case Left(MalformedContent(error, _)) => Reject(MalformedQueryParamRejection(error, paramName))
      case Left(x: UnsupportedContentType) => throw new IllegalStateException(x.toString)
    }
  } 
  implicit def forString(implicit fsod: FSOD[String]) = extractParameter[String, String] { string =>
    filter(string, fsod)
  }
  implicit def forSymbol(implicit fsod: FSOD[String]) = extractParameter[Symbol, String] { symbol =>
    filter(symbol.name, fsod)
  }
  implicit def forNDesR[T] = extractParameter[NameDeserializerReceptacle[T], T] { nr =>
    filter(nr.name, nr.deserializer)
  }
  implicit def forNDefR[T](implicit fsod: FSOD[T]) = extractParameter[NameDefaultReceptacle[T], T] { nr =>
    filter(nr.name, fsod.withDefaultValue(nr.default))
  }
  implicit def forNDesDefR[T] = extractParameter[NameDeserializerDefaultReceptacle[T], T] { nr =>
    filter(nr.name, nr.deserializer.withDefaultValue(nr.default))
  }
  implicit def forNR[T](implicit fsod: FSOD[T]) = extractParameter[NameReceptacle[T], T] { nr =>
    filter(nr.name, fsod)
  }


  /************ required parameter support ******************/

  private def requiredFilter(paramName: String, fsod: FSOD[_], requiredValue: Any) =
    BasicDirectives.filter { ctx =>
      fsod(ctx.request.queryParams.get(paramName)) match {
        case Right(value) if value == requiredValue => Pass.Empty
        case _ => Reject.Empty
      }
    }
  implicit def forRVR[T](implicit fsod: FSOD[T]) = ParamDefMagnetAux[RequiredValueReceptacle[T], Directive0] { rvr =>
    requiredFilter(rvr.name, fsod, rvr.requiredValue)
  }
  implicit def forRVDR[T] = ParamDefMagnetAux[RequiredValueDeserializerReceptacle[T], Directive0] { rvr =>
    requiredFilter(rvr.name, rvr.deserializer, rvr.requiredValue)
  }


  /************ tuple support ******************/

  implicit def forTuple[T <: Product, L <: HList, Out]
    (implicit hla: HListerAux[T, L], pdma: ParamDefMagnetAux[L, Out]) =
    ParamDefMagnetAux[T, Out](tuple => pdma(hla(tuple)))


  /************ HList support ******************/

  implicit def forHList[L <: HList](implicit f: LeftFolder[L, Directive0, MapReduce.type]) =
    ParamDefMagnetAux[L, f.Out](_.foldLeft(BasicDirectives.noop)(MapReduce))

  object MapReduce extends Poly2 {
    implicit def from[T, LA <: HList, LB <: HList, Out <: HList]
      (implicit pdma: ParamDefMagnetAux[T, Directive[LB]], ev: PrependAux[LA, LB, Out]) =
      at[Directive[LA], T] { (a, t) => a & pdma(t) }
  }
}