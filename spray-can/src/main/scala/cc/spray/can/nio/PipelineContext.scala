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

package cc.spray.can.nio

import java.nio.channels.Channel

abstract class PipelineContext[Input, Output] { outer =>
  def input: Input

  def push(output: Output)

  def channel: Channel

  def close()

  def withInput[T](newInput: T) = new PipelineContext[T, Output] {
    def input = newInput
    def push(output: Output) { outer.push(output) }
    def channel = outer.channel
    def close() { outer.close() }
  }

  def withPush[T](f: T => Unit) = new PipelineContext[Input, T] {
    def input = outer.input
    def push(output: T) { f(output) }
    def channel = outer.channel
    def close() { outer.close() }
  }
}