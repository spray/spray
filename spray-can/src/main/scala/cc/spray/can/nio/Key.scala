/*
 * Copyright (C) 2011, 2012 Mathias Doenitz
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

package cc.spray.can.nio

import java.nio.channels.{Channel, SelectionKey}
import java.nio.ByteBuffer
import collection.mutable.ListBuffer

sealed abstract class Key {
  def channel: Channel

  private[nio] def selectionKey: SelectionKey

  private[nio] val writeBuffers = ListBuffer.empty[ListBuffer[ByteBuffer]]

  private[nio] def enable(ops: Int) {
    selectionKey.interestOps(selectionKey.interestOps() | ops)
  }
  private[nio] def disable(ops: Int) {
    selectionKey.interestOps(selectionKey.interestOps() & ~ops)
  }
}

private[nio] object Key {
  def apply(key: SelectionKey) = new Key {
    def channel = key.channel
    private[nio] def selectionKey = key
  }
}