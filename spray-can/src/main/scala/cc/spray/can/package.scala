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

import collection.immutable.LinearSeq
import akka.actor.{ActorRef, Actor}
import java.io.{BufferedReader, InputStreamReader}

package object can {
  def make[A, U](a: A)(f: A => U): A = { f(a); a }

  lazy val SprayCanVersion: String = {
    new BufferedReader(new InputStreamReader(getClass.getResourceAsStream("/version"))).readLine()
  }

  private[can] def actor(id: String): ActorRef = {
    val actors = Actor.registry.actorsFor(id)
    assert(actors.length == 1, actors.length + " actors for id '" + id + "' found, expected exactly one")
    actors.head
  }

  def isTokenChar(c: Char) = c match {
    case x if 'a' <= x && x <= 'z' => true
    case x if 'A' <= x && x <= 'Z' => true
    case '-' => true
    case '(' | ')' | '<' | '>' | '@' | ',' | ';' | ':' | '\\' | '"' | '/' | '[' | ']' | '?' | '=' | '{' | '}' => false
    case x => 32 < x && x < 127
  }

  // implicits
  implicit def pimpLinearSeq[A](seq: LinearSeq[A]): PimpedLinearSeq[A] = new PimpedLinearSeq[A](seq)
  implicit def pimpByteArray(array: Array[Byte]): PimpedByteArray = new PimpedByteArray(array)

  val EmptyByteArray = new Array[Byte](0)
}