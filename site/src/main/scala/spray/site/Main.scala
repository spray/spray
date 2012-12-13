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

import akka.actor.Props
import spray.can.server.SprayCanHttpServerApp


object Main extends App with SprayCanHttpServerApp {
  import system.log

  log.info("Loading sphinx content root...")
  val root = new RootNode(SphinxDoc.load("index/").getOrElse(sys.error("index doc not found")))
  val blog = new Blog(root)

  log.info("Starting service actor and HTTP server...")
  val service = system.actorOf(Props[SiteServiceActor], "site-service")
  newHttpServer(service) ! Bind(SiteSettings.Interface, SiteSettings.Port)
}

class Blog(rootNode: RootNode) {
  val root = rootNode.find("blog/").getOrElse(sys.error("blog root not found"))
  val posts: Map[String, Seq[ContentNode]] = root.children.flatMap { node =>
    if (node.doc.post.isEmpty) sys.error(s"blog root contains non-post child $node")
    node.post.tags.map(_ -> node)
  } groupBy(_._1) mapValues(_.map(_._2))
}