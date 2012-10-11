package cc.spray.can.spdy
package pipeline

import cc.spray.io.pipelining.{PipelineContext, CommandPipelineStage}
import cc.spray.io.{IOServer, Command}

object SpdyRendering {
  def apply(): CommandPipelineStage = new CommandPipelineStage {
    def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): CPL = {
      val renderer = new rendering.SpdyRenderer

      {
        case SendSpdyFrame(frame) =>
          commandPL(IOServer.Send(renderer.renderFrame(frame)))

        case x =>
          println("Got command "+x)
          commandPL(x)
      }
    }
  }

  // COMMANDS
  case class SendSpdyFrame(frame: Frame) extends Command
}
