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

package spray.can.parsing

import com.typesafe.config.{ConfigFactory, Config}


private[can] class ParserSettings(config: Config = ConfigFactory.load.getConfig("spray.can.parsing")) {
  config.checkValid(ConfigFactory.defaultReference.getConfig("spray.can.parsing"))

  val MaxUriLength            = config getBytes "max-uri-length"
  val MaxResponseReasonLength = config getBytes "max-response-reason-length"
  val MaxHeaderNameLength     = config getBytes "max-header-name-length"
  val MaxHeaderValueLength    = config getBytes "max-header-value-length"
  val MaxHeaderCount          = config getBytes "max-header-count"
  val MaxContentLength        = config getBytes "max-content-length"
  val MaxChunkExtNameLength   = config getBytes "max-chunk-ext-name-length"
  val MaxChunkExtValueLength  = config getBytes "max-chunk-ext-value-length"
  val MaxChunkExtCount        = config getBytes "max-chunk-ext-count"
  val MaxChunkSize            = config getBytes "max-chunk-size"

  require(MaxUriLength            > 0, "max-uri-length must be > 0")
  require(MaxResponseReasonLength > 0, "max-response-reason-length must be > 0")
  require(MaxHeaderNameLength     > 0, "max-header-name-length must be > 0")
  require(MaxHeaderValueLength    > 0, "max-header-value-length must be > 0")
  require(MaxHeaderCount          > 0, "max-header-count must be > 0")
  require(MaxContentLength        > 0, "max-content-length must be > 0")
  require(MaxChunkExtNameLength   > 0, "max-chunk-ext-name-length must be > 0")
  require(MaxChunkExtValueLength  > 0, "max-chunk-ext-value-length must be > 0")
  require(MaxChunkExtCount        > 0, "max-chunk-ext-count must be > 0")
  require(MaxChunkSize            > 0, "max-chunk-size must be > 0")
}
