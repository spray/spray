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

import utils.AkkaConfSettings

object SprayServerSettings extends AkkaConfSettings("spray.") {
  lazy val RootActorId                = configString("spray-root-service")
  lazy val TimeoutActorId             = configString("spray-root-service")
  lazy val RequestTimeout             = configInt(1000)
  lazy val RootPath                   = configString
  lazy val FileChunkingThresholdSize  = configLong(Long.MaxValue) // in bytes
  lazy val FileChunkingChunkSize      = configInt(512 * 1024) // 512 KB

  warnOnUndefinedExcept("CompactJsonPrinting", "LoggingTarget")
}