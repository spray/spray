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

package spray


package object io {
  type Pipeline[-T] = T => Unit
  type Connection = IOBridge.Connection

  implicit def pimpBooleanWithOptionalPipelineStageOperator(condition: Boolean) = new PimpedBoolean(condition)
  class PimpedBoolean(condition: Boolean) {
    // unfortunately we cannot use the nicer right-associative `?:` operator due to
    // https://issues.scala-lang.org/browse/SI-1980
    def ? (stage: => PipelineStage) = if (condition) stage else EmptyPipelineStage
  }
}

package io {

  trait Command

  trait Event

  trait Droppable // marker for Commands and Events
}