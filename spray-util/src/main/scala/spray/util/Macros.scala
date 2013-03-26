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

package spray.util

import scala.reflect.macros.Context
import scala.concurrent.duration.Duration

private[spray] object Macros {

  def requirePositiveOrUndefined(c: Context)(duration: c.Expr[Duration]) = {
    import c.universe._
    val name = duration match {
      case c.Expr(Ident(n))     ⇒ n
      case c.Expr(Select(_, n)) ⇒ n
      case c.Expr(x)            ⇒ sys.error(s"requirePositiveOrUndefined cannot be used with argument $x: ${x.getClass}")
    }
    val msg: c.Expr[String] = c.Expr(Literal(Constant(s"requirement failed: $name must be > 0 or 'infinite'")))
    reify {
      if (duration.splice <= Duration.Zero)
        throw new IllegalArgumentException(msg.splice)
      duration.splice
    }
  }

}