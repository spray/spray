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

package spray.http

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec

private[http] trait ObjectRegistry[K, V <: AnyRef] {
  private[this] val _registry = new AtomicReference(Map.empty[K, V])

  @tailrec
  protected final def register(key: K, obj: V): obj.type = {
    val reg = registry
    val updated = reg.updated(key, obj)
    if (_registry.compareAndSet(reg, updated)) obj
    else register(key, obj)
  }

  protected def registry: Map[K, V] = _registry.get

  def getForKey(key: K): Option[V] = registry.get(key)
}

