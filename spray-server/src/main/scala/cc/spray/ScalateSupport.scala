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

package cc.spray

import http.ContentType
import http.MediaTypes._
import org.fusesource.scalate.{Binding, TemplateEngine}

trait ScalateSupport {
  this: Directives =>

  val templateEngine = new TemplateEngine

  def render(uri: String, attributes: Map[String,Any] = Map.empty,
             extraBindings: Traversable[Binding] = Nil, contentType: ContentType = `text/html`): Route = {
    respondWithContentType(contentType) {
      completeWith {
        templateEngine.layout(uri, attributes, extraBindings)
      }
    }
  }
}