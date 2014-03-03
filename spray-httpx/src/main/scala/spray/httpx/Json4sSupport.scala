/*
 * Copyright © 2011-2013 the spray project <http://spray.io>
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
package spray.httpx

import org.json4s.native.Serialization

/**
 * Supplies the serialization and deserialization formats for JSON4s.
 *
 * proper usage
 * formats = DefaultFormats(NoTypeHints)
 * if you want extra support add json4s-ext to dependencies and add
 *
 * all examples taken from json4s.org site:
 * Scala enums
 * implicit val formats = org.json4s.DefaultFormats + new org.json4s.ext.EnumSerializer(MyEnum)
 * or for enum names
 * implicit val formats = org.json4s.DefaultFormats + new org.json4s.ext.EnumNameSerializer(MyEnum)
 * Joda Time
 * implicit val formats = org.json4s.DefaultFormats ++ org.json4s.ext.JodaTimeSerializers.all
 */
trait Json4sSupport extends BaseJson4sSupport {
  protected[httpx] def serialization = Serialization
}
