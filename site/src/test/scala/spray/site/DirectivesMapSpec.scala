/*
 * Copyright © 2011-2015 the spray project <http://spray.io>
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
import org.specs2.mutable.Specification

import spray.json._
import DefaultJsonProtocol._

class DirectivesMapSpec extends Specification {
  case class GroupEntry(group: String, entries: String)
  object GroupEntry {
    implicit val entryFormat = jsonFormat2(GroupEntry.apply)
  }

  "The generated `directives-map.js` file" should {
    "contain expected entries" in {
      val content = FileUtils.readAllTextFromResource("theme/js/directives-map.js")
      content must not(beNull)
      content.startsWith("window.DirectivesMap = [") must beTrue
      content.endsWith("];\n") must beTrue

      val lines = content.split("\n").drop(1).dropRight(1)
      val jsonString = "[" + lines.mkString.dropRight(1) /* trailing comma */ + "]"
      val json = JsonParser(jsonString)
      val entries = json.convertTo[List[GroupEntry]]

      // ensure basic invariants
      entries.forall(e ⇒ e.group.nonEmpty && e.entries.nonEmpty) must beTrue

      // find some well-known entries
      val fileAndResource = entries.find(_.group == "file-and-resource").get
      fileAndResource.entries must contain("getFromFile")
      fileAndResource.entries must contain("getFromResource")

      val path = entries.find(_.group == "path").get
      path.entries must contain("path")
      path.entries must contain("pathPrefix")

      val route = entries.find(_.group == "route").get
      route.entries must contain("complete")
      route.entries must contain("reject")
    }
  }
}
