/*
 * Copyright (C) 2011, 2012 Mathias Doenitz
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
package cc.spray.can.model


object Trailer {
  def verify(trailer: List[HttpHeader]) = {
    if (!trailer.isEmpty) {
      require(trailer.forall(_.name != "Content-Length"), "Content-Length header is not allowed in trailer")
      require(trailer.forall(_.name != "Transfer-Encoding"), "Transfer-Encoding header is not allowed in trailer")
      require(trailer.forall(_.name != "Trailer"), "Trailer header is not allowed in trailer")
    }
    trailer
  }
}