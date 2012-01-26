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

/**
 * Sealed trait modelling an HTTP method.
 * All defined methods are declared as members of the `HttpMethods` object.
 */
sealed trait HttpMethod {
  def name: String
}

/**
 * Module containing all defined [[cc.spray.can.HttpMethod]] instances.
 */
object HttpMethods {
  class Method private[HttpMethods] (val name: String) extends HttpMethod {
    override def toString = name
  }
  val GET     = new Method("GET")
  val POST    = new Method("POST")
  val PUT     = new Method("PUT")
  val DELETE  = new Method("DELETE")
  val HEAD    = new Method("HEAD")
  val OPTIONS = new Method("OPTIONS")
  val TRACE   = new Method("TRACE")
  val CONNECT = new Method("CONNECT")
}