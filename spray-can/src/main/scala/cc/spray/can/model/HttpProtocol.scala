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
 * Sealed trait modelling an HTTP protocol version.
 * All defined protocols are declared as members of the `HttpProtocols` object.
 */
sealed trait HttpProtocol {
  def name: String
}

/**
 * Module containing all defined [[cc.spray.can.HttpProtocol]] instances.
 */
object HttpProtocols {
  class Protocol private[HttpProtocols] (val name: String) extends HttpProtocol {
    override def toString = name
  }
  val `HTTP/1.0` = new Protocol("HTTP/1.0")
  val `HTTP/1.1` = new Protocol("HTTP/1.1")
}