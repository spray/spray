package cc.spray
package examples.markdownserver

import cc.spray._
import http._
import http.MediaTypes._
import org.pegdown.{Extensions, PegDownProcessor}

trait MarkdownService extends Directives {
  
  val MarkdownType = register(CustomMediaType("text/x-markdown", "markdown", "mdown", "md"))

  val markdownService = {
    pathPrefix("doc") {
      cache {
        transformResponse(_.withContentTransformed(markdown2Html)) {
          getFromResourceDirectory("www", pathRewriter = rewritePath)
        }
      }
    }
  }
  
  def rewritePath(path: String) = (if (path.isEmpty) "index" else path) + ".markdown"
  
  def markdown2Html(content: HttpContent) = content.contentType match {
    case ContentType(MarkdownType, _) => {
      val html = new PegDownProcessor(Extensions.ALL).markdownToHtml(content.as[String].right.get) 
      HttpContent(ContentType(`text/html`), html) 
    }
    case _ => content
  }
  
}