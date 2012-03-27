package cc.spray.io

import org.specs2.mutable.Specification

class ByteBufferBuilderSpec extends Specification {

  "A ByteBufferBuilder" should {
    "initially be empty" in {
      ByteBufferBuilder() must haveContent("")
      ByteBufferBuilder(16) must haveContent("")
    }
    "properly collect ASCII string content within capacity" in {
      ByteBufferBuilder(16).append("Yeah") must haveContent("Yeah")
    }
    "properly collect ASCII string content exceeding capacity" in {
      ByteBufferBuilder(8).append("Yeah").append(" ").append("absolutely!") must haveContent("Yeah absolutely!")
    }
    "properly collect byte array content within capacity" in {
      ByteBufferBuilder(16).append("Yeah".getBytes) must haveContent("Yeah")
    }
    "properly collect byte array content exceeding capacity" in {
      ByteBufferBuilder(8).append("Yeah".getBytes).append(" ".getBytes).append("absolutely!".getBytes) must
        haveContent("Yeah absolutely!")
    }
  }

  def haveContent(s: String) = beEqualTo(s) ^^ { bb: ByteBufferBuilder =>
    val buf = bb.toByteBuffer
    val sb = new java.lang.StringBuilder
    while (buf.remaining > 0) sb.append(buf.get.toChar)
    sb.toString
  }
}
