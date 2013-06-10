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

import akka.actor.{ ActorSystem, Props }
import akka.event.Logging
import akka.io.IO
import spray.can.Http
import spray.http.StringRendering

object Main extends App {
  implicit val system = ActorSystem("site")
  val log = Logging(system, getClass)
  val settings = SiteSettings(system)

  log.info("Loading sphinx content root...")
  val root = new RootNode(SphinxDoc.load("index/").getOrElse(sys.error("index doc not found")))
  val blog = new Blog(root)

  //  println(root.render(new StringRendering).get)
  //  system.shutdown()
  log.info("Starting service actor and HTTP server...")
  val service = system.actorOf(Props(new SiteServiceActor(settings)), "site-service")
  IO(Http) ! Http.Bind(service, settings.interface, settings.port)
}

class Blog(rootNode: RootNode) {
  val root = rootNode.find("blog/").getOrElse(sys.error("blog root not found"))
  val posts: Map[String, Seq[ContentNode]] = root.children.flatMap { node ⇒
    if (node.doc.post.isEmpty) sys.error(s"blog root contains non-post child $node")
    node.post.tags.map(_ -> node)
  } groupBy (_._1) mapValues (_.map(_._2))
}