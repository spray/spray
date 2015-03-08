/*
 * Copyright © 2011-2015 the spray project <http://spray.io>
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

import java.security.Principal
import java.security.cert.Certificate
import javax.net.ssl.{ SSLEngine, SSLSession, SSLPeerUnverifiedException }

/** Information about an SSL session. */
case class SSLSessionInfo(
  cipherSuite: String,
  localCertificates: List[Certificate],
  localPrincipal: Option[Principal],
  peerCertificates: List[Certificate],
  peerPrincipal: Option[Principal])

object SSLSessionInfo {

  def apply(engine: SSLEngine): SSLSessionInfo =
    apply(engine.getSession)

  def apply(session: SSLSession): SSLSessionInfo = {
    val localCertificates = Option(session.getLocalCertificates).map { _.toList } getOrElse Nil
    val localPrincipal = Option(session.getLocalPrincipal)
    val peerCertificates =
      try session.getPeerCertificates.toList
      catch { case e: SSLPeerUnverifiedException ⇒ Nil }
    val peerPrincipal =
      try Option(session.getPeerPrincipal)
      catch { case e: SSLPeerUnverifiedException ⇒ None }
    SSLSessionInfo(session.getCipherSuite, localCertificates, localPrincipal, peerCertificates, peerPrincipal)
  }
}

