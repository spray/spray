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


private[spray] trait SuffixReceptaclePimps {
  implicit def fromSymbol(name: Symbol) = NameTypeReceptacle[String](name.name)
  implicit def fromString(name: String) = NameTypeReceptacle[String](name)
}

sealed trait SuffixReceptacle[A]
sealed trait ExtractionReceptacle[A] extends SuffixReceptacle[A]
case class NameTypeReceptacle[A](name: String) extends ExtractionReceptacle[A] {
  def as[B] = NameTypeReceptacle[B](name)
  def ? = as[Option[A]]
  def ? [B](default: B) = NameTypeDefaultReceptable(name, default)
  def ! [B](requiredValue: B) = RequiredValueReceptable(name, requiredValue)
}
case class NameTypeDefaultReceptable[A](name: String, default: A) extends ExtractionReceptacle[A]
case class RequiredValueReceptable[A](name: String, requiredValue: A) extends SuffixReceptacle[A]