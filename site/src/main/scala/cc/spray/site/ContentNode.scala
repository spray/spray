/*
 * Copyright (C) 2011-2012 spray.cc
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

package cc.spray.site

import xml.{Node, XML}
import java.lang.{ StringBuilder => JStringBuilder }
import cc.spray.util._


sealed trait ContentNode {
  def name: String
  def uri: String
  def body: String
  def children: Seq[ContentNode]
  def isRoot: Boolean
  def parent: ContentNode
  def isLast = parent.children.last == this
  def isLeaf = children.isEmpty
  def level: Int = if (isRoot) 0 else parent.level + 1
  def absoluteUri = if (uri.startsWith("http://")) uri else "/" + uri

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
  def body = ""
  val children = (xml \ "ul" \ "li") map li2TocTree(this)
  def isRoot = true
  def parent = this

  private def li2TocTree(_parent: ContentNode)(li: Node): ContentNode = new ContentNode {
    val a = (li \ "a").head
    val name = a.text
    val uri = {
      val u = (a \ "@href").text
      if (u.endsWith("/")) u.dropRight(1) else u.replace("/#", "#")
    }
    val children: Seq[ContentNode] = (li \ "ul" \ "li").map(li2TocTree(this))(collection.breakOut)
    def isRoot = false
    def parent = _parent
    lazy val body = if (uri.startsWith("http://")) "" else loadBody()

    def loadBody() = {
      val body = SphinxDoc.load(uri.takeWhile(_ != '#')) match {
        case Some(SphinxDoc(b)) => b
        case None => SphinxDoc.load(uri + "/index")
          .getOrElse(throw new RuntimeException("SphinxDoc for uri '%s' not found" format uri))
          .body
      }
      body // TODO: fix internal references in the body, if they cannot be found in the node tree
    }
  }
}