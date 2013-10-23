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

package spray.site

import org.parboiled.common.FileUtils
import scala.annotation.tailrec
import scala.xml.{ Node, XML }
import spray.http.Rendering
import spray.util._

sealed trait ContentNode {
  def title: String
  def name: String
  def uri: String
  def children: Seq[ContentNode]
  def isRoot: Boolean
  def parent: ContentNode
  def doc: SphinxDoc
  def loadUri: String
  def post: BlogPost = doc.post.getOrElse(sys.error(s"$uri is not a blog-post"))
  def isLast = parent.children.last == this
  def isLeaf = children.isEmpty
  def level: Int = if (isRoot) 0 else parent.level + 1
  def absoluteUri = if (uri.startsWith("http") || uri.startsWith("/")) uri else "/" + uri
  def isDescendantOf(node: ContentNode): Boolean = node == this || !isRoot && parent.isDescendantOf(node)

  def find(uri: String): Option[ContentNode] =
    if (uri == this.uri) Some(this)
    else children.mapFind(_.find(uri))

  def render[R <: Rendering](r: R, prefix: String = ""): r.type =
    if (children.nonEmpty) {
      r ~~ prefix ~~ name ~~ ": " ~~ uri ~~ '\n'
      children foreach (_.render(r, prefix + "  "))
      r
    } else r
}

abstract class BranchRootNode(val title: String, val name: String, val uri: String, val loadUri: String,
                              val doc: SphinxDoc, docVersion: String, extraChildren: Seq[ContentNode] = Nil) extends ContentNode {
  val children: Seq[ContentNode] = {
    def findTocTreeWrapper(n: Node): Node =
      if (n.attribute("class").get.text.startsWith("toctree-wrapper")) n else findTocTreeWrapper((n \ "div").head)
    extraChildren ++ (findTocTreeWrapper(XML.loadString(doc.body)) \ "ul" \ "li").par.map(SubNode(this, docVersion)).seq
  }
}

class RootNode(doc: SphinxDoc) extends BranchRootNode("REST/HTTP for your Akka/Scala Actors", "root", "", "", doc, "") {
  def isRoot = true
  def parent = this
}

object SubNode {
  private final val DOC_URI = "documentation/"
  def apply(parent: ContentNode, docVersion: String, extraChildren: Seq[ContentNode] = Nil)(li: Node): ContentNode = {
    val a = (li \ "a").head
    val rawUri = (a \ "@href").text
    if (rawUri == DOC_URI && docVersion.isEmpty)
      docParentNode(parent, li)
    else {
      val name = if (rawUri == DOC_URI) docVersion else a.text
      val (uri, loadUri) =
        if (docVersion.nonEmpty) {
          if (rawUri.startsWith(DOC_URI)) (DOC_URI + docVersion + '/' + rawUri.substring(DOC_URI.length)) -> rawUri
          else (DOC_URI + docVersion + '/' + rawUri) -> ("documentation-" + docVersion + '/' + rawUri)
        } else rawUri -> rawUri
      new SubNode(li, docVersion, name, uri, loadUri, parent, extraChildren)
    }
  }

  def docParentNode(_parent: ContentNode, li: Node): ContentNode =
    new ContentNode { docRoot ⇒
      def title = "Documentation"
      def name = title
      def uri = DOC_URI
      def loadUri = ""
      def isRoot = false
      def parent = _parent
      val doc = SphinxDoc(FileUtils.readAllTextFromResource("documentation-root.html"), "documentation", PostMetaData())
      val children: Seq[ContentNode] = {
        val other = Main.settings.otherVersions map { v ⇒
          SphinxDoc.load(s"documentation-$v/index/") match {
            case Some(d) ⇒
              new BranchRootNode("Documentation » " + v, v, DOC_URI + v + '/', "documentation-" + v, d, v) {
                def isRoot = false
                def parent = docRoot
              }
            case None ⇒ sys.error(s"index.fjson for documentation version $v not found")
          }
        }
        val nodes = other ++ APIDocNode.findFor(_parent, Main.settings.mainVersion) :+ SubNode(this, Main.settings.mainVersion)(li)
        nodes.sortBy(_.name)
      }
    }
}

class SubNode(li: Node, docVersion: String,
              val name: String, val uri: String, val loadUri: String, val parent: ContentNode, extraChildren: Seq[ContentNode] = Nil) extends ContentNode {
  def title = if (parent.isRoot) name else parent.title + " » " + name
  val children: Seq[ContentNode] = extraChildren ++ (li \ "ul" \ "li").map(SubNode(this, docVersion))(collection.breakOut)
  private[this] var lastDoc: Option[SphinxDoc] = None
  def doc: SphinxDoc = lastDoc.getOrElse {
    import SphinxDoc.load
    val loaded =
      if (!uri.contains("#")) {
        val d = load(loadUri).orElse(load(loadUri + "index/")).getOrElse(sys.error(s"SphinxDoc for uri '$loadUri' not found"))
        if (loadUri != uri) {
          val documentationBranchRootName = SubNode.DOC_URI + docVersion + '/'
          @tailrec def levelUp(node: ContentNode = this, dots: String = "../"): String =
            if (node.uri == documentationBranchRootName) dots else levelUp(node.parent, dots + "../")
          val dots = levelUp()
          d.copy(body = d.body.replace("href=\"" + dots, "href=\"../" + dots).replace("src=\"" + dots, "src=\"../" + dots))
        } else d
      } else SphinxDoc.Empty
    if (!Main.settings.devMode) lastDoc = Some(loaded)
    loaded
  }
  def isRoot = false
}

case class APIDocNode(_parent: ContentNode, docVersion: String) extends ContentNode {
  def title: String = "API (snapshot)"
  def name = title
  def children = Nil
  def uri = "documentation/" + docVersion + "/api/"
  def isRoot = true
  def doc = ???
  def loadUri = ???
  def parent = _parent
}

object APIDocNode {
  def findFor(_parent: ContentNode, version: String): Seq[APIDocNode] =
    if (getClass.getClassLoader.getResource("api/" + version + "/index.html") ne null)
      Seq(APIDocNode(_parent, version))
    else Nil
}
