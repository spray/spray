package spray.io.openssl

import org.bridj.Pointer
import java.nio.ByteBuffer

/**
 * A wrapper around BridJ's native Pointers.
 */
class DirectBuffer(_size: Int) {
  val pointer = Pointer.allocateBytes(size)

  def address: Long = pointer.getPeer
  //def asByteBuffer: ByteBuffer = pointer.getByteBuffer
  def get(buffer: Array[Byte]) {
    pointer.getBytes(buffer)
  }
  def get(buffer: Array[Byte], size: Int) {
    pointer.getBytesAtOffset(0, buffer, 0, size)
  }
  def set(buffer: Array[Byte]) {
    pointer.setBytes(buffer)
  }

  /**
   *  Copies as much remaining bytes from the ByteBuffer into this direct buffer.
   *  Note: This does not change the position of the byte buffer. You have to adjust
   *  this manually.
   */
  def setFromByteBuffer(buffer: ByteBuffer): Int = {
    val numBytes = math.min(_size, buffer.remaining())
    pointer.setBytesAtOffset(0, buffer, buffer.position(), numBytes)
    numBytes
  }

  def copyToByteBuffer(size: Int): ByteBuffer = {
    val bytes = new Array[Byte](size)
    get(bytes, size)

    ByteBuffer.wrap(bytes)
  }

  def size: Int = _size
}
object DirectBuffer {
  def forCString(string: String): DirectBuffer = {
    val buf = new DirectBuffer(string.length + 1)
    buf.pointer.setCString(string)
    buf
  }
}
