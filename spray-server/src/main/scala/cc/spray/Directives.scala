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

import directives._
import typeconversion._

/**
 * Directives is the central trait you should mix in to get access to ''sprays'' Route building directives.
 */
trait Directives
        extends BasicDirectives
        with CacheDirectives
        with CodecDirectives
        with DetachDirectives
        with FileAndResourceDirectives
        with FormFieldDirectives
        with MarshallingDirectives
        with MiscDirectives
        with ParameterDirectives
        with PathDirectives
        with SecurityDirectives
        with SimpleDirectives
        with FromStringDeserializers

object Directives extends Directives