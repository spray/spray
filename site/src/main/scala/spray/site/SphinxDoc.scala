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

package spray.site

import org.parboiled.common.FileUtils
import spray.json._


object JsonProtocol extends DefaultJsonProtocol {
  implicit val sphinxJsonFormat = jsonFormat1(SphinxDoc.apply)
}

case class SphinxDoc(body: String)

object SphinxDoc {
  import JsonProtocol._

  def load(docPath: String): Option[SphinxDoc] = {
    val path = if (docPath.endsWith("/")) docPath.dropRight(1) else docPath
    loadFrom("sphinx/json/%s.fjson".format(path))
  }

  def loadFrom(resourceName: String): Option[SphinxDoc] = {
    val nullableJsonSource = FileUtils.readAllTextFromResource(resourceName)
    Option(nullableJsonSource).map { jsonSource =>
      val patchedJsonSource = jsonSource.replace("\\u00b6", "&#182;").replace(""" border=\"1\"""", "")
      val jsonAst = patchedJsonSource.asJson
      jsonAst.convertTo[SphinxDoc]
    }
  }
}