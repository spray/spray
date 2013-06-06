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

package spray.routing.authentication

import com.typesafe.config.{ ConfigException, Config }
import akka.dispatch.{ ExecutionContext, Promise }
import spray.caching.{ LruCache, Cache }
import spray.util.pimpString_

object UserPassAuthenticator {

  def apply[T](f: UserPassAuthenticator[T]) = f

  /**
   * Creats a UserPassAuthenticator that uses plain-text username/password definitions from a given
   * spray/akka config file section for authentication. The config section should look like this:
   * {{{
   *   spray.routing.users {
   *     username = "password"
   *     ...
   *   }
   * }}}
   */
  def fromConfig[T](config: Config)(createUser: UserPass ⇒ T)(implicit ec: ExecutionContext): UserPassAuthenticator[T] = { userPassOption ⇒
    Promise.successful(
      userPassOption.flatMap { userPass ⇒
        try {
          val pw = config.getString(userPass.user)
          if (pw secure_== userPass.pass) Some(createUser(userPass)) else None
        } catch {
          case _: ConfigException ⇒ None
        }
      }).future
  }

  /**
   * Creates a wrapper around an UserPassAuthenticator providing authentication lookup caching using the given cache.
   * Note that you need to manually add a dependency to the spray-caching module in order to be able to use this method.
   */
  def cached[T](inner: UserPassAuthenticator[T], cache: Cache[Option[T]] = LruCache[Option[T]]())(implicit ec: ExecutionContext): UserPassAuthenticator[T] = { userPassOption ⇒
    cache.fromFuture(userPassOption) {
      inner(userPassOption)
    }
  }
}
