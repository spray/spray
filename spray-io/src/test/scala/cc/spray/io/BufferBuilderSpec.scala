package cc.spray.io

import org.specs2.mutable.Specification

class BufferBuilderSpec extends Specification {

  "A BufferBuilder" should {
    "initially be empty" in {
      BufferBuilder() must haveContent("")
      BufferBuilder(16) must haveContent("")
    }
    "properly collect ASCII string content within capacity" in {
      BufferBuilder(16).append("Yeah") must haveContent("Yeah")
    }
    "properly collect ASCII string content exceeding capacity" in {
      BufferBuilder(8).append("Yeah").append(" ").append("absolutely!") must haveContent("Yeah absolutely!")
    }
    "properly collect byte array content within capacity" in {
      BufferBuilder(16).append("Yeah".getBytes) must haveContent("Yeah")
    }
    "properly collect byte array content exceeding capacity" in {
      BufferBuilder(8).append("Yeah".getBytes).append(" ".getBytes).append("absolutely!".getBytes) must
        haveContent("Yeah absolutely!")
    }
    "properly produce results as per toArray" in {
      BufferBuilder(4).append("Hello").toArray === Array(72, 101, 108, 108, 111)
    }
  }

  def haveContent(s: String) = beEqualTo(s) ^^ { bb: BufferBuilder =>
    val buf = bb.toByteBuffer
    val sb = new java.lang.StringBuilder
    while (buf.remaining > 0) sb.append(buf.get.toChar)
    sb.toString
  }
}
