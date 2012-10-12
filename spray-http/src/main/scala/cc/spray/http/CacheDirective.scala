/*
 * Copyright (C) 2011-2012 spray.io
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team (http://github.com/jdegoes/blueeyes)
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

package spray.http

import scala.Product

sealed trait CacheDirective extends Product {
  val name = productPrefix.replace("$minus", "-")
  def value: String = name
  override def toString = value
}

object CacheDirectives {
  sealed trait RequestDirective extends CacheDirective
  sealed trait ResponseDirective extends CacheDirective

  /* Requests and Responses */
  case object `no-cache` extends RequestDirective with ResponseDirective
  case object `no-store` extends RequestDirective with ResponseDirective
  case object `no-transform` extends RequestDirective with ResponseDirective
  case class `max-age`(deltaSeconds: Long) extends RequestDirective with ResponseDirective {
    override def value = name + "=" + deltaSeconds
  }
  case class CustomCacheDirective(name_ :String, content: Option[String])  extends RequestDirective with ResponseDirective {
    override val name = name_
    override def value = name + content.map("=\"" + _ + '"').getOrElse("")
  }

  /* Requests only */
  case class `max-stale`(deltaSeconds: Option[Long]) extends RequestDirective {
    override def value = name + deltaSeconds.map("=" + _).getOrElse("")
  }
  case class `min-fresh`(deltaSeconds: Long) extends RequestDirective {
    override def value = name + "=" + deltaSeconds
  }
  case object `only-if-cached` extends RequestDirective

  /* Responses only */
  case object `public` extends ResponseDirective
  case class `private`(fieldNames :Seq[String] = Nil) extends ResponseDirective {
    override def value = name + (if (fieldNames.isEmpty) "" else fieldNames.mkString("=\"", ",", "\""))
  }
  case class `no-cache`(fieldNames: Seq[String] = Nil) extends ResponseDirective {
    override def value = name + (if (fieldNames.isEmpty) "" else fieldNames.mkString("=\"", ",", "\""))
  }
  case object `must-revalidate` extends ResponseDirective
  case object `proxy-revalidate` extends ResponseDirective
  case class `s-maxage`(deltaSeconds: Long)  extends ResponseDirective {
    override def value = name + "=" + deltaSeconds
  }
}