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

import http.HttpResponse

sealed trait RoutingResult
case class Respond(response: HttpResponse) extends RoutingResult

sealed trait FilterResult[+A]
case class Reject(rejections: Set[Rejection] = Set.empty) extends FilterResult[Nothing] with RoutingResult
case class Pass[+A](values: List[A] = Nil, transform: RequestContext => RequestContext = identity) extends FilterResult[A]

object Reject {
  def apply(rejection: Rejection): Reject = apply(Set(rejection))
}