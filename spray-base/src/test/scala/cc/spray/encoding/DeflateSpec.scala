package cc.spray.encoding

import org.specs2.mutable.Specification
import java.util.zip.{InflaterOutputStream, DeflaterOutputStream}
import java.io.{ByteArrayOutputStream}

class DeflateSpec extends Specification with CodecSpecSupport {

  "The Deflate codec" should {
    def ourDeflate(bytes: Array[Byte]) = Deflate.newEncodingContext.encode(bytes)
    def ourInflate(bytes: Array[Byte]) = Deflate.newDecodingContext.decode(bytes)

    "properly encode a small string" in {
      streamInflate(ourDeflate(smallTextBytes)) must readAs(smallText)
    }
    "properly decode a small string" in {
      ourInflate(streamDeflate(smallTextBytes)) must readAs(smallText)
    }
    "properly roundtip encode/decode a small string" in {
      ourInflate(ourDeflate(smallTextBytes)) must readAs(smallText)
    }
    "properly encode a large string" in {
      streamInflate(ourDeflate(largeTextBytes)) must readAs(largeText)
    }
    "properly decode a large string" in {
      ourInflate(streamDeflate(largeTextBytes)) must readAs(largeText)
    }
    "properly roundtip encode/decode a large string" in {
      ourInflate(ourDeflate(largeTextBytes)) must readAs(largeText)
    }
    "provide a better compression ratio than the standard Deflater/Inflater streams" in {
      ourDeflate(largeTextBytes).length must be_< (streamDeflate(largeTextBytes).length)
    }
    "support chunked round-trip encoding/decoding" in {
      val chunks = largeTextBytes.grouped(512).toArray
      val encCtx = Deflate.newEncodingContext
      val decCtx = Deflate.newDecodingContext
      val chunks2 = chunks.map(chunk => decCtx.decode(encCtx.encodeChunk(chunk))) :+ decCtx.decode(encCtx.finish())
      chunks2.flatten must readAs(largeText)
    }
  }

  def streamDeflate(bytes: Array[Byte]) = {
    val output = new ByteArrayOutputStream()
    val dos = new DeflaterOutputStream(output); dos.write(bytes); dos.close()
    output.toByteArray
  }

  def streamInflate(bytes: Array[Byte]) = {
    val output = new ByteArrayOutputStream()
    val ios = new InflaterOutputStream(output); ios.write(bytes); ios.close()
    output.toByteArray
  }
}