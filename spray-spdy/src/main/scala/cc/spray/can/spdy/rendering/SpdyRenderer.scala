package cc.spray.can.spdy
package rendering

import java.nio.ByteBuffer
import cc.spray.http.HttpEntity
import java.io.{ByteArrayOutputStream, ByteArrayInputStream}
import java.util.zip.{Inflater, Deflater, DeflaterOutputStream}
import cc.spray.can.parsing.{IntermediateState, ParsingState}
import cc.spray.httpx.encoding.DeflateCompressor
import parsing.FrameHeaderReader
import annotation.tailrec

class SpdyRenderer {
  import Spdy2._
  import Flags._

  val compressor = new DeflateCompressor {
    override def dictionary: Option[Array[Byte]] = Some(Spdy2.dictionary)
  }

  def renderFrame(frame: Frame): ByteBuffer = frame match {
    case syn: SynStream =>
      renderSynStream(syn)
    case SynReply(id, fin, kvs) =>
      renderSynReply(id, fin, kvs)

    case h: Headers =>
      renderHeaders(h)

    case DataFrame(id, fin, data) =>
      renderDataFrame(id, fin, data)
  }

  def renderSynStream(syn: SynStream): ByteBuffer = {
    val dataBuffer = renderKeyValues(syn.keyValues)
    val length = dataBuffer.limit + 10

    val buffer = ByteBuffer.allocate(10000)

    def putByte(b: Int) {
      require(b < 256)
      buffer.put(b.toByte)
    }

    putByte(0x80) // Control frame
    putByte(0x02) // version 2
    buffer.putShort(ControlFrameTypes.SYN_STREAM.toShort) // type 2 SYN_REPLY
    putByte(FLAG_FIN(syn.fin) | FLAG_FIN(syn.unidirectional)) // flags
    putByte((length >> 16) & 0xff)
    putByte((length >> 8) & 0xff)
    putByte((length) & 0xff)
    buffer.putInt(syn.streamId & 0x7fffffff)
    buffer.putInt(syn.associatedTo & 0x7fffffff)
    putByte(syn.priority << 6)
    putByte(0)
    buffer.put(dataBuffer)
    buffer.flip()

    check(buffer.slice())

    buffer
  }

  def renderSynReply(streamId: Int, fin: Boolean, keyValues: Map[String, String]): ByteBuffer = {
    val dataBuffer = renderKeyValues(keyValues)
    val length = dataBuffer.limit + 6

    //println("Length is "+length)

    val buffer = ByteBuffer.allocate(10000)

    def putByte(b: Int) {
      require(b < 256)
      buffer.put(b.toByte)
    }

    putByte(0x80) // Control frame
    putByte(0x02) // version 2
    buffer.putShort(ControlFrameTypes.SYN_REPLY.toShort) // type 2 SYN_REPLY
    putByte(if (fin) 0x01 else 0) // flags
    putByte((length >> 16) & 0xff)
    putByte((length >> 8) & 0xff)
    putByte((length) & 0xff)
    buffer.putInt(streamId & 0x7fffffff)
    buffer.putShort(0) // reserved
    buffer.put(dataBuffer)
    buffer.flip()

    check(buffer.slice())

    buffer
  }
  def renderHeaders(headers: Headers): ByteBuffer = {
    val dataBuffer = renderKeyValues(headers.keyValues)
    val length = dataBuffer.limit + 6

    //println("Length is "+length)

    val buffer = ByteBuffer.allocate(10000)

    def putByte(b: Int) {
      require(b < 256)
      buffer.put(b.toByte)
    }

    putByte(0x80) // Control frame
    putByte(0x02) // version 2
    buffer.putShort(ControlFrameTypes.HEADERS.toShort) // type 2 SYN_REPLY
    putByte(0) // flags
    putByte((length >> 16) & 0xff)
    putByte((length >> 8) & 0xff)
    putByte((length) & 0xff)
    buffer.putInt(headers.streamId & 0x7fffffff)
    buffer.putShort(0) // reserved
    buffer.put(dataBuffer)
    buffer.flip()

    check(buffer.slice())

    buffer
  }

  def check(buffer: ByteBuffer) = {
    /*println("Total bytes: "+buffer.limit()+", "+buffer.remaining())
    val buf = new Array[Byte](buffer.limit)
    buffer.slice().get(buf)
    println("Bytes: "+buf.map(_ formatted "%02x").mkString(" "))
    println("Result is "+readToEnd(new FrameHeaderReader(new Inflater), buffer))*/
  }
  def readToEnd(state: ParsingState, buffer: ByteBuffer): ParsingState = state match {
    case x: IntermediateState if buffer.remaining() > 0 =>
      val oldRemaining = buffer.remaining()
      val read = x.read(buffer)
      if (oldRemaining == buffer.remaining())
        throw new IllegalStateException("Parser makes no progress")
      else
        readToEnd(read, buffer)
    case x => x
  }

  def renderDataFrame(streamId: Int, fin: Boolean, data: Array[Byte]): ByteBuffer = {
    val buffer = ByteBuffer.allocate(1000000)

    def putByte(b: Int) {
      require(b < 256)
      buffer.put(b.toByte)
    }
    buffer.putInt(streamId)
    putByte(if (fin) 0x01 else 0) // flags
    val length = data.length
    putByte((length >> 16) & 0xff)
    putByte((length >> 8) & 0xff)
    putByte((length) & 0xff)
    buffer.put(data)

    buffer.flip()
    check(buffer.slice())
    buffer
  }

  def renderKeyValues(keyValues: Map[String, String]): ByteBuffer = {
    val os = new ByteArrayOutputStream()

    def u2(i: Int) {
      require(i < Short.MaxValue)
      os.write(i >> 8)
      os.write(i & 0xff)
    }
    def utf8String(str: String) {
      val bytes = str.getBytes("utf8")
      u2(bytes.length)
      os.write(bytes)
    }

    u2(keyValues.size)
    keyValues.foreach {
      case (k, v) =>
        utf8String(k)
        utf8String(v)
    }
    os.close()

    ByteBuffer.wrap(compressor.compress(os.toByteArray).flush())
  }
}

object TestRendering extends App {
  val result = (new SpdyRenderer).renderSynReply(1, false, Map("status" -> "200", "version" -> "HTTP/1.1", "test" -> "blub"))
  println(result)
}