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
  private[can] def toTcpWriteCommand(data: HttpData, ack: Tcp.Event): Tcp.WriteCommand =
    data match {
      case HttpData.Empty                ⇒ Tcp.Write.empty
      case HttpData.Compound(head, tail) ⇒ toTcpWriteCommand2(head, Tcp.NoAck) +: toTcpWriteCommand(tail, ack)
      case x: HttpData.SimpleNonEmpty    ⇒ toTcpWriteCommand2(x, ack)
    }

  private[can] def toTcpWriteCommand2(data: HttpData.SimpleNonEmpty, ack: Tcp.Event): Tcp.SimpleWriteCommand =
    data match {
      case HttpData.Bytes(byteString)                ⇒ Tcp.Write(byteString, ack)
      case HttpData.FileBytes(fileName, offset, len) ⇒ Tcp.WriteFile(fileName, offset, len, ack)
    }
}
