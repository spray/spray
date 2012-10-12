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

package spray.util
package pimps

import java.lang.reflect.{GenericArrayType, ParameterizedType, Type, Array => RArray}

class PimpedClass[A](underlying: Class[A]) {

  /**
   * Get the actual type arguments used to extend the given generic base class or interface.
   * (Based on code copyright 2007 by Ian Robertson)
   *
   * @param base the generic base class or interface
   * @return a list of the raw classes for the actual type arguments.
   */
  def getTypeArgumentsOf(base: Class[_]): List[Option[Class[_]]] = {

    // Get the underlying class for t, or None if t is a variable type.
    def getClass(t: Type): Option[Class[_]] = t match {
      case x: Class[_] => Some(x)
      case x: ParameterizedType => getClass(x.getRawType)
      case x: GenericArrayType => getClass(x.getGenericComponentType).map(c => RArray.newInstance(c, 0).getClass())
      case _ => None
    }

    // the Type parameters for t along with a map of type parameters to type arguments
    def typeArguments(t: Type): Option[(List[Type], Map[Type, Type])] = t match {
      case x if getClass(x) == Some(base) => {
        // terminate recursion
        x match {
          case x: Class[_] => Some((x.getTypeParameters.toList, Map.empty[Type, Type]))
          case p: ParameterizedType => Some(
            (p.getActualTypeArguments.toList,
              p.getRawType.asInstanceOf[Class[_]].getTypeParameters.zip(p.getActualTypeArguments).toMap)
          )
        }
      }
      case x: Class[_] => {
        val supers = Option(x.getGenericSuperclass).toList ::: x.getGenericInterfaces.toList
        supers.mapFind(typeArguments(_))
      }
      case p: ParameterizedType => {
        val clazz = p.getRawType.asInstanceOf[Class[_]]
        typeArguments(clazz).map {
          case (actualTypeArguments, params2args) =>
            (actualTypeArguments, params2args ++ clazz.getTypeParameters.zip(p.getActualTypeArguments))
        }
      }
      case _ => None
    }

    def resolve(t: Type, params2args: Map[Type, Type]): Type = params2args.get(t) match {
      case Some(x) => resolve(x, params2args)
      case None => t
    }

    typeArguments(underlying).map {
      case (typeArgs, params2args) =>
        typeArgs.map {
          baseType => getClass(resolve(baseType, params2args))
        }
    }.getOrElse(Nil)
  }

}