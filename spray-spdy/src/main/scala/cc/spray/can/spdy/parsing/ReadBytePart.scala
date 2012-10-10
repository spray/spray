package cc.spray.can.spdy.parsing

import cc.spray.can.parsing.{ParsingState, IntermediateState}
import java.nio.ByteBuffer

/**
 * An intermediate state that reads numBytes and then delegates to the
 * finished method to read those bytes.
 * @param numBytes
 */
abstract class ReadBytePart(var numBytes: Int) extends IntermediateState {
  val buffer = new Array[Byte](numBytes)

  def finished(bytes: Array[Byte]): ParsingState

  def read(buf: ByteBuffer): ParsingState = {
    val toRead = math.min(numBytes, buf.remaining)
    buf.get(buffer, buffer.length - numBytes, toRead)

    val remaining = numBytes - toRead
    if (remaining > 0) {
      numBytes = remaining
      this
    } else
      finished(buffer)
  }

  override def toString: String = "Parser %s at position %d of %d" format (getClass.getSimpleName, buffer.length - numBytes, buffer.length)
}
