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

package spray.can

import spray.http.HttpData
import akka.io.Tcp

package object rendering {
  def toTcpWriteCommand(data: HttpData, ack: Tcp.Event): Tcp.WriteCommand =
    data match {
      case HttpData.Empty ⇒ Tcp.Write.empty
      case _: HttpData.Compound ⇒
        // inefficient work-around, to be removed when https://github.com/akka/akka/pull/1709
        // is merged and available in a release
        Tcp.Write(data.toByteString, ack)
      case x: HttpData.SimpleNonEmpty ⇒ toTcpWriteCommand(x, ack)
    }

  def toTcpWriteCommand(data: HttpData.SimpleNonEmpty, ack: Tcp.Event): Tcp.WriteCommand =
    data match {
      case HttpData.Bytes(byteString)                ⇒ Tcp.Write(byteString, ack)
      case HttpData.FileBytes(fileName, offset, len) ⇒ Tcp.WriteFile(fileName, offset, len, ack)
    }
}
