package cc.spray.nio

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