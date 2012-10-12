/*
 * Copyright (C) 2011-2012 spray.io
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

package spray.io

import java.nio.channels.{SocketChannel, SelectionKey}
import collection.mutable.Queue

sealed abstract class Key {
  def channel: SocketChannel

  private[io] def selectionKey: SelectionKey

  // the writeQueue contains instances of either ByteBuffer, PerformCleanClose or AckTo
  // we don't introduce a dedicated algebraic datatype for this since we want to save the extra
  // allocation that would be required for wrapping the ByteBuffers
  private[io] val writeQueue = Queue.empty[AnyRef]

  private[this] var _ops = 0

  private[io] def enable(ops: Int) {
    if ((_ops & ops) == 0) {
      _ops |= ops
      selectionKey.interestOps(_ops)
    }
  }

  private[io] def disable(ops: Int) {
    if ((_ops & ops) != 0) {
      _ops &= ~ops
      selectionKey.interestOps(_ops)
    }
  }
}

private[io] object Key {
  def apply(key: SelectionKey) = new Key {
    def channel = key.channel.asInstanceOf[SocketChannel]
    private[io] def selectionKey = key
  }
}