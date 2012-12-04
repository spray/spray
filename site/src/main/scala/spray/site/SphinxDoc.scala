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
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext
import spray.caching.LruCache
import spray.json._
import spray.util.pimpFuture


object JsonProtocol extends DefaultJsonProtocol {
  implicit val postMetaDataFormat = jsonFormat(PostMetaData, "author", "tags", "index-paragraphs")
  implicit val sphinxJsonFormat = jsonFormat3(SphinxDoc.apply)
}

case class PostMetaData(author: Option[String], tags: Option[String], indexParagraphs: Option[String]) {
  val tagList: List[String] = tags.map(_.split(',').map(_.trim)).toList.flatten
}

case class SphinxDoc(body: String, current_page_name: String, meta: PostMetaData)

object SphinxDoc {
  import JsonProtocol._

  private val cache = LruCache[Option[SphinxDoc]](timeToLive = Duration(1, "s"))

  def load(docPath: String) = {
    import ExecutionContext.Implicits.global
    require(docPath.endsWith("/"))
    cache(docPath) {
      loadFrom("sphinx/json/%s.fjson" format docPath.dropRight(1))
    }.await
  }

  private def loadFrom(resourceName: String): Option[SphinxDoc] = {
    val nullableJsonSource = FileUtils.readAllTextFromResource(resourceName)
    Option(nullableJsonSource).map { jsonSource =>
      val patchedJsonSource = jsonSource.replace("\\u00b6", "&#182;").replace(""" border=\"1\"""", "")
      val jsonAst = patchedJsonSource.asJson
      jsonAst.convertTo[SphinxDoc]
    }
  }
}