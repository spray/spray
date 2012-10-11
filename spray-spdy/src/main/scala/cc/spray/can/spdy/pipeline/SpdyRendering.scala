package cc.spray.can.spdy
package pipeline

import cc.spray.io._

object SpdyRendering {
  def apply(): CommandPipelineStage = new CommandPipelineStage {
    def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): CPL = {
      val renderer = new rendering.SpdyRenderer

      {
        case SendSpdyFrame(frame) => commandPL(IOServer.Send(renderer.renderFrame(frame)))
        case x => commandPL(x)
      }
    }
  }

  // COMMANDS
  case class SendSpdyFrame(frame: Frame) extends Command
}
