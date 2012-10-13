package cc.spray.can.spdy
package parsing

import cc.spray.can.parsing.{IntermediateState, ParsingState}
import java.nio.ByteBuffer
import java.util.zip.{InflaterInputStream, Inflater, Deflater, DeflaterInputStream}
import java.io.ByteArrayInputStream
import annotation.tailrec

class FrameHeaderReader(inflater: Inflater) extends ReadBytePart(8) {
  def finished(bytes: Array[Byte]): ParsingState = {
    val Array(_, _, _, _, _, l1, l2, l3) = bytes

    val length = Conversions.u3be(l1, l2, l3)
    val res = new FrameDataReader(inflater, bytes, length)

    // TODO: think more about where this shortcut really belongs
    if (length == 0)
      res.finished(Array.empty)
    else
      res
  }
}

class FrameDataReader(inflater: Inflater, header: Array[Byte], length: Int) extends ReadBytePart(length) {
  import Conversions._
  import Spdy2._
  import ControlFrameTypes._
  import Flags._

  def finished(dataBytes: Array[Byte]): ParsingState = {
    val Array(h1, h2, h3, h4, flagsB, _*) = header
    val flags = b2i(flagsB)

    if ((h1 & 0x80) != 0) { // control frame
      val version = u2be(h1, h2) & 0x7fff // strip off control frame bit

      if (version != 2) {
        println("Got unsupported version "+version)
        FrameParsingError(ErrorCodes.UNSUPPORTED_VERSION)
      }
      else {
        val tpe = u2be(h3, h4)

        tpe match {
          case SYN_STREAM =>
            val streamId = u4be(dataBytes(0), dataBytes(1), dataBytes(2), dataBytes(3)) & 0x7fffffff
            val associatedTo = u4be(dataBytes(4), dataBytes(5), dataBytes(6), dataBytes(7)) & 0x7fffffff
            val prio = (b2i(dataBytes(8)) & 0xc0) >> 6

            val headers = readHeaders(inflater, dataBytes.drop(10))

            SynStream(streamId, associatedTo, prio, FLAG_FIN(flags), FLAG_UNIDIRECTIONAL(flags), headers)

          case SYN_REPLY =>
            val streamId = u4be(dataBytes(0), dataBytes(1), dataBytes(2), dataBytes(3)) & 0x7fffffff
            val headers = readHeaders(inflater, dataBytes.drop(6))

            SynReply(streamId, FLAG_FIN(flags), headers)

          case RST_STREAM =>
            val streamId = u4be(dataBytes(0), dataBytes(1), dataBytes(2), dataBytes(3)) & 0x7fffffff
            val statusCode = u4be(dataBytes(4), dataBytes(5), dataBytes(6), dataBytes(7))

            assert(flags == 0)

            RstStream(streamId, statusCode)

          case SETTINGS =>
            val numSettings = u4be(dataBytes(0), dataBytes(1), dataBytes(2), dataBytes(3))

            def readSetting(idx: Int): Setting = {
              val offset = 4 + idx * 8
              val id = u3le(dataBytes(offset), dataBytes(offset + 1), dataBytes(offset + 2))
              val flags = b2i(dataBytes(offset + 3))
              val value = u4be(dataBytes(offset + 4), dataBytes(offset + 5), dataBytes(offset + 6), dataBytes(offset + 7))

              Setting(id, flags, value)
            }

            val settings = (0 until numSettings).map(readSetting)

            Settings(Flags.FLAG_SETTINGS_CLEAR_PREVIOUSLY_PERSISTED_SETTINGS(flags), settings)

          case PING =>
            val id = u4be(dataBytes(0), dataBytes(1), dataBytes(2), dataBytes(3))
            Ping(id, header ++ dataBytes)
          case _ =>
            FrameParsingError(ErrorCodes.PROTOCOL_ERROR)
        }
      }
    } else { // data frame
      val streamId = u4be(h1, h2, h3, h4)
      DataFrame(streamId, FLAG_FIN(flags), dataBytes)
    }
  }

  def readHeaders(inflater: Inflater, data: Array[Byte]): Map[String, String] = {
    def dump(data: Array[Byte]) =
      println("Data "+data.length+" is "+data.map(_ formatted "%02x").mkString(" "))

    inflater.setInput(data)

    //dump(data)


    val buf = new Array[Byte](1000)
    val read = inflater.inflate(buf)
    if (read == 0 && inflater.needsDictionary()) {
      println("Need dictionary")
      inflater.setDictionary(dictionary)
    }

    var cur = read
    //while(!inflater.finished()) {
      val read2 = inflater.inflate(buf, cur, 1000 - cur)
      cur += read
    //}
    //println("Finished "+inflater.finished())

    val is = new ByteArrayInputStream(buf)
    //dump(buf)


    def u2(): Int = u2be(is.read.toByte, is.read.toByte)
    def bytes(num: Int): Array[Byte] = {
      val res = new Array[Byte](num)

      var cur = 0
      while (cur < num) {
        //println("Trying to read %d:%d for %d" format (cur, num-cur, num))
        val read = is.read(res, cur, num - cur)
        cur += read
      }
      res
    }
    def utf8StringOfLength(length: Int): String =
      new String(bytes(length), "utf8")
    def utf8String(): String =
      utf8StringOfLength(u2())

    val entries = u2()
    //println("Should read %d entries" format entries)

    @tailrec def readEntries(remaining: Int, current: Seq[(String, String)]): Seq[(String, String)] = {
      if (remaining > 0) {
        val key = utf8String()
        val value = utf8String()

        readEntries(remaining - 1, current :+ (key, value))
      } else
        current
    }
    readEntries(entries, Nil).toMap
  }
}
