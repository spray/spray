/*
 * Copyright (C) 2011-2013 spray.io
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
import scala.xml.XML
import spray.http.DateTime
import spray.json._

object JsonProtocol extends DefaultJsonProtocol {
  implicit val postMetaDataFormat = jsonFormat(PostMetaData, "author", "tags", "index-paragraphs", "show-post-structure", "scripts", "styles")
  implicit val sphinxJsonFormat = jsonFormat(SphinxDoc.apply, "body", "current_page_name", "meta")
}

case class PostMetaData(
  author: Option[String],
  tags: Option[String],
  indexParagraphs: Option[String],
  showPostStructure: Option[String],
  scripts: Option[String],
  styles: Option[String]
  ) {
  val tagList: List[String] = tags.map(_.split(',').map(_.trim)).toList.flatten
  val scriptList: List[String] = scripts.map(_.split(' ')).toList.flatten
  val styleList: List[String] = styles.map(_.split(' ')).toList.flatten
}

case class SphinxDoc(body: String, current_page_name: String, meta: PostMetaData) {
  val post: Option[BlogPost] = {
    val date = current_page_name.substring(current_page_name.lastIndexOf("/") + 1).take(10) + "T00:00:00"
    (DateTime.fromIsoDateTimeString(date), meta.author) match {
      case (None, None)                   ⇒ None
      case (Some(dateTime), Some(author)) ⇒ Some(new BlogPost(dateTime, author, body, meta))
      case (Some(_), None)                ⇒ sys.error(s"$current_page_name has no author meta data field")
      case (None, Some(_))                ⇒ sys.error(s"$current_page_name has an author meta data field but is not named like a blog post")
    }
  }
}

object SphinxDoc {
  import JsonProtocol._

  val Empty = SphinxDoc("", "", PostMetaData(None, None, None, None, None, None))

  def load(docPath: String): Option[SphinxDoc] = {
    require(docPath.endsWith("/"), s"$docPath URI doesn't end with a slash")
    val resourceName = "sphinx/json/%s.fjson" format docPath.dropRight(1)
    val nullableJsonSource = FileUtils.readAllTextFromResource(resourceName)
    Option(nullableJsonSource).map { jsonSource ⇒
      val patchedJsonSource = jsonSource
        .replace("\\u00b6", "&#182;")
        .replace("&mdash;", "&#8212;")
        .replace(""" border=\"1\"""", "")
      val jsonAst = patchedJsonSource.asJson
      jsonAst.convertTo[SphinxDoc]
    }
  }
}

class BlogPost(val dateTime: DateTime, val author: String, _body: String, meta: PostMetaData) {
  def date = dateTime.toIsoDateString
  def tags = meta.tagList
  def indexParagraphs = (XML.loadString(body) \\ "p").take(meta.indexParagraphs.fold(1)(_.toInt))
  def tagLinks = tags.map(tag ⇒ s"""<a href="/blog/category/$tag/">$tag</a>""").mkString(", ")
  def body = {
    val meta = s"""</h1><div class="post-meta">Posted on $date by <em>$author</em>, tags: $tagLinks</div>"""
    _body.replaceFirst("</h1>", meta)
  }
  def showStructure: Boolean = meta.showPostStructure == Some("yes") | meta.showPostStructure == Some("true")
}