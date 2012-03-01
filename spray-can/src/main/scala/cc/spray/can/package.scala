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

package cc.spray

import can.util.{PimpedByteArray, PimpedFuture, PimpedLinearSeq}
import collection.immutable.LinearSeq
import java.io.{BufferedReader, InputStreamReader}
import akka.dispatch.Future

package object can {

  lazy val SprayCanVersion: String = {
    new BufferedReader(new InputStreamReader(getClass.getResourceAsStream("/spray-can.version"))).readLine()
  }

  // implicits
  implicit def pimpLinearSeq[A](seq: LinearSeq[A]): PimpedLinearSeq[A] = new PimpedLinearSeq[A](seq)
  implicit def pimpByteArray(array: Array[Byte]): PimpedByteArray = new PimpedByteArray(array)
  implicit def pimpFuture[A](fut: Future[A]): PimpedFuture[A] = new PimpedFuture[A](fut)
}