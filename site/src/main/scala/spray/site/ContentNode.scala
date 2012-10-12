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

import xml.{Node, XML}
import java.lang.{ StringBuilder => JStringBuilder }
import spray.util._


sealed trait ContentNode {
  def name: String
  def uri: String
  def children: Seq[ContentNode]
  def isRoot: Boolean
  def parent: ContentNode
  def isLast = parent.children.last == this
  def isLeaf = children.isEmpty
  def level: Int = if (isRoot) 0 else parent.level + 1
  def absoluteUri = if (uri.startsWith("http") || uri.startsWith("/")) uri else "/" + uri
  def isDescendantOf(node: ContentNode): Boolean = node == this || !isRoot && parent.isDescendantOf(node)

  def find(uri: String): Option[ContentNode] = {
    if (uri == this.uri) Some(this)
    else children.mapFind(_.find(uri))
  }

  override def toString: String = {
    val sb = new JStringBuilder
    format(sb, "")
    sb.toString.dropRight(1)
  }

  private def format(sb: JStringBuilder, indent: String) {
    sb.append(indent).append(name).append(": ").append(uri).append("\n")
    children.foreach(_.format(sb, indent + "  "))
  }
}

object RootNode extends ContentNode {
  private val xml = {
    val model = SphinxDoc.load("index").get
    XML.loadString(model.body)
  }

  def name = "root"
  def uri = ""
  val children = (xml \ "ul" \ "li") map li2Node(this)
  def isRoot = true
  def parent = this

  private def li2Node(_parent: ContentNode)(li: Node): ContentNode = new ContentNode {
    val a = (li \ "a").head
    val name = a.text
    val uri = (a \ "@href").text
    val children: Seq[ContentNode] = (li \ "ul" \ "li").map(li2Node(this))(collection.breakOut)
    def isRoot = false
    def parent = _parent
  }
}