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

package cc.spray.routing.directives

import cc.spray.httpx.unmarshalling.{FromStringOptionDeserializer => FSOD, Deserializer}


sealed class NameReceptacle[T](val name: String, val deserializer: FSOD[T]) {
  def as[B](implicit fsod: FSOD[B]) = new NameReceptacle(name, fsod)
  def ? = as[Option[T]](Deserializer.liftToTargetOption(deserializer))
  def ? [B](default: B)(implicit fsod: FSOD[B]) = new NameReceptacle(name, fsod.withDefaultValue(default))
  def ! [B](requiredValue: B)(implicit fsod: FSOD[B]) = new RequiredValueReceptacle(name, requiredValue, fsod)
}

trait ToNameReceptaclePimps {
  implicit def symbol2NR(symbol: Symbol)(implicit fsod: FSOD[String]) = new NameReceptacle(symbol.name, fsod)
  implicit def string2NR(string: String)(implicit fsod: FSOD[String]) = new NameReceptacle(string, fsod)
}

class RequiredValueReceptacle[T](val name: String, val requiredValue: T, val deserializer: FSOD[T])