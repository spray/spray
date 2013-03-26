/*
 * Copyright (C) 2011-2012 spray.io
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team (http://github.com/jdegoes/blueeyes)
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

private[http] trait ObjectRegistry[K, V] {
  private[this] val registry = new AtomicReference(Map.empty[K, V])

  @tailrec
  final def register(key: K, obj: V) {
    val current = registry.get
    val updated = current.updated(key, obj)
    if (!registry.compareAndSet(current, updated))
      register(key, obj)
  }

  def getForKey(key: K): Option[V] = registry.get.get(key)
}

