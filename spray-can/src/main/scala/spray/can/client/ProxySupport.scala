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

package spray.can.client

import spray.util._
import spray.can.Http.{ ConnectionType, HostConnectorSetup }
import spray.http.Uri

private[can] object ProxySupport {
  private final val * = '*'

  // see http://docs.oracle.com/javase/6/docs/technotes/guides/net/proxies.html
  def validIgnore(pattern: String) = pattern.exists(_ != *) && !pattern.drop(1).dropRight(1).contains(*)

  def resolveAutoProxied(normalizedSetup: HostConnectorSetup) = {
    import normalizedSetup._
    val resolved =
      if (sslEncryption) ConnectionType.Direct // TODO
      else connection match {
        case ConnectionType.AutoProxied ⇒
          val scheme = Uri.httpScheme(sslEncryption)
          val proxySettings = settings.get.connectionSettings.proxySettings.get(scheme)
          proxyFor(host, proxySettings) match {
            case Some((proxyHost, proxyPort)) ⇒ ConnectionType.Proxied(proxyHost, proxyPort)
            case None                         ⇒ ConnectionType.Direct
          }
        case x ⇒ x
      }
    normalizedSetup.copy(connection = resolved)
  }

  def proxyFor(host: String, settings: Option[ProxySettings]): Option[(String, Int)] =
    settings flatMap {
      case ProxySettings(proxyHost, proxyPort, ignorePatterns) ⇒
        def matches(pattern: String) = {
          val matchStart = pattern endsWith *
          val matchEnd = pattern startsWith *
          val (start, end, check): (Int, Int, (String, String) ⇒ Boolean) = (matchStart, matchEnd) match {
            case (true, true)  ⇒ (1, 1, _ contains _)
            case (true, false) ⇒ (0, 1, _ startsWith _)
            case (false, true) ⇒ (1, 0, _ endsWith _)
            case _             ⇒ (0, 0, _ equals _)
          }
          check(host, pattern.substring(start, pattern.length - end))
        }
        if (ignorePatterns exists matches) None else Some((proxyHost, proxyPort))
    }
}