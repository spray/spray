package cc.spray.can

import model.{HttpProtocols, HttpMethods, HttpResponse}
import org.specs2.mutable.Specification
import cc.spray.io.test.PipelineStageTest
import rendering.HttpResponsePartRenderingContext
import cc.spray.io.{CleanClose, IoPeer, Command}

class ResponseRenderingSpec extends Specification with PipelineStageTest {
  val fixture = new Fixture(
    ResponseRendering(
      serverHeader = "spray/1.0",
      chunklessStreaming = false,
      responseSizeHint = 256
    )
  )

  "The ResponseRendering PipelineStage" should {
    "be transparent to unrelated commands" in {
      val command = new Command {}
      fixture(command).commands === Seq(command)
    }
    "translate a simple HttpResponsePartRenderingContext into the corresponding Send command" in {
      fixture(
        HttpResponsePartRenderingContext(
          responsePart = HttpResponse(body = "Some Message".getBytes),
          requestMethod = HttpMethods.GET,
          requestProtocol = HttpProtocols.`HTTP/1.1`,
          requestConnectionHeader = None
        )
      ).commands.fixSends === Seq(SendString(
        """|HTTP/1.1 200 OK
           |Server: spray/1.0
           |Date: XXXX
           |Content-Length: 12
           |
           |Some Message"""
      ))
    }
    "append a Close command to the Send if the connection is to be closed" in {
      fixture(
        HttpResponsePartRenderingContext(
          responsePart = HttpResponse(body = "Some Message".getBytes),
          requestMethod = HttpMethods.HEAD,
          requestProtocol = HttpProtocols.`HTTP/1.1`,
          requestConnectionHeader = Some("close")
        )
      ).commands.fixSends === Seq(
        SendString(
          """|HTTP/1.1 200 OK
             |Connection: close
             |Server: spray/1.0
             |Date: XXXX
             |Content-Length: 12
             |
             |"""
        ),
        IoPeer.Close(CleanClose)
      )
    }
  }

  step {
    cleanup()
  }
}
