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

package cc.spray
package authentication

import caching.Cache

/**
 * Stackable trait to be mixed into a UserPassAuthenticator.
 * Provides the underlying UserPassAuthenticator with authentication lookup caching.
 */
trait CachingAuthenticator[U] extends UserPassAuthenticator[U] {

  protected def authCache: Cache[Option[U]]

  abstract override def apply(userPass: Option[(String, String)]) = {
    authCache.fromFuture(userPass) {
      super.apply(userPass)
    }
  }
}