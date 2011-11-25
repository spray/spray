package cc.spray
package encoding

import org.specs2.mutable.Specification
import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import org.parboiled.common.FileUtils
import java.util.zip.{ZipException, GZIPInputStream, GZIPOutputStream}

class GzipSpec extends Specification with CodecSpecSupport {

  "The Gzip codec" should {
    "properly encode a small string" in {
      streamGunzip(ourGzip(smallTextBytes)) must readAs(smallText)
    }
    "properly decode a small string" in {
      ourGunzip(streamGzip(smallTextBytes)) must readAs(smallText)
    }
    "properly roundtip encode/decode a small string" in {
      ourGunzip(ourGzip(smallTextBytes)) must readAs(smallText)
    }
    "properly encode a large string" in {
      streamGunzip(ourGzip(largeTextBytes)) must readAs(largeText)
    }
    "properly decode a large string" in {
      ourGunzip(streamGzip(largeTextBytes)) must readAs(largeText)
    }
    "properly roundtip encode/decode a large string" in {
      ourGunzip(ourGzip(largeTextBytes)) must readAs(largeText)
    }
    "provide a better compression ratio than the standard Gzipr/Gunzip streams" in {
      ourGzip(largeTextBytes).length must be_< (streamGzip(largeTextBytes).length)
    }
    "properly decode concatenated compressions" in {
      ourGunzip(Array(gzip("Hello,"), gzip(" dear "), gzip("User!")).flatten) must readAs("Hello, dear User!")
    }
    "throw an error on corrupt input" in {
      ourGunzip(corruptGzipContent) must throwA[ZipException]("invalid literal/length code")
    }
    "not throw an error if a subsequent block is corrupt" in {
      ourGunzip(Array(gzip("Hello,"), gzip(" dear "), corruptGzipContent).flatten) must readAs("Hello, dear ")
    }
    "support chunked round-trip encoding/decoding" in {
      val chunks = largeTextBytes.grouped(512).toArray
      val encCtx = Gzip.newEncodingContext
      val decCtx = Gzip.newDecodingContext
      val chunks2 = chunks.map(chunk => decCtx.decode(encCtx.encodeChunk(chunk))) :+ decCtx.decode(encCtx.finish())
      chunks2.flatten must readAs(largeText)
    }
  }

  def gzip(s: String) = ourGzip(s.getBytes("UTF8"))
  def ourGzip(bytes: Array[Byte]) = Gzip.newEncodingContext.encode(bytes)
  def ourGunzip(bytes: Array[Byte]) = Gzip.newDecodingContext.decode(bytes)

  lazy val corruptGzipContent = utils.make(gzip("Hello")) { _.update(14, 26.toByte) }

  def streamGzip(bytes: Array[Byte]) = {
    val output = new ByteArrayOutputStream()
    val gos = new GZIPOutputStream(output); gos.write(bytes); gos.close()
    output.toByteArray
  }

  def streamGunzip(bytes: Array[Byte]) = {
    val output = new ByteArrayOutputStream()
    FileUtils.copyAll(new GZIPInputStream(new ByteArrayInputStream(bytes)), output)
    output.toByteArray
  }

}