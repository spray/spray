package cc.spray.http

/*
 * Copyright (C) 2011 Mathias Doenitz
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

trait ObjectRegistry[K, V] {
  protected val registry = collection.mutable.Map.empty[K, V]
  
  protected[spray] def register(obj: V, keys: Seq[K]) {
    keys.foreach(register(obj, _))
  }
  
  protected[spray] def register(obj: V, key: K) {
    registry.update(key, obj)
  }
  
  def getForKey(key: K): Option[V] = {
    registry.get(key)
  }
}