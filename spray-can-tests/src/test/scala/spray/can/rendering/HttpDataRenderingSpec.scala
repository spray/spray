package spray.can.rendering

import org.specs2.mutable.Specification
import akka.util.ByteString
import spray.http.HttpData
import akka.io.Tcp
import java.io.{ FileOutputStream, File }

class HttpDataRenderingSpec extends Specification {
  val bytes = ByteString(Array.fill(10)('a'.toByte))
  val file = {
    val res = File.createTempFile("test", "dat").getCanonicalFile
    val os = new FileOutputStream(res)
    os.write(Array.fill(2000)('b'.toByte))
    os.close()
    res.deleteOnExit()
    res
  }

  "HttpData" should {
    "render correctly" in {
      "Bytes" in {
        toTcpWriteCommand(HttpData(bytes), CustomAck) === Tcp.Write(bytes, CustomAck)
      }
      "FileBytes" in {
        toTcpWriteCommand(HttpData(file, 1000, 500), CustomAck) === Tcp.WriteFile(file.getCanonicalPath, 1000, 500, CustomAck)
      }
      "Simple Compound" in {
        toTcpWriteCommand(HttpData(bytes) +: HttpData(file, 1000, 500), CustomAck) ===
          (Tcp.Write(bytes) +:
            Tcp.WriteFile(file.getCanonicalPath, 1000, 500, CustomAck))
      }
      "Long Compound" in {
        val data = HttpData(ByteString('x'.toByte))
        val longCompound = Vector.fill(100000)(data).reduceRight(_ +: _)
        val writes = toTcpWriteCommand(longCompound, CustomAck).asInstanceOf[Iterable[Tcp.SimpleWriteCommand]].toSeq
        val writeData = writes.collect { case Tcp.Write(byteString, _) ⇒ byteString }.reduceLeft(_ ++ _)
        writeData.utf8String === ("x" * 100000)
      }
    }
  }

  object CustomAck extends Tcp.Event
}
