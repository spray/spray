package cc.spray.can.spdy.pipeline

import cc.spray.io.pipelining.{Pipelines, PipelineContext, DoublePipelineStage}
import cc.spray.io.{Command, Event}

object HttpOnSpdy {
  def apply(): DoublePipelineStage = new DoublePipelineStage {
    def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): BuildResult = new Pipelines {
      def eventPipeline: (Event) => Unit = {
        case x => eventPL(x)
      }

      def commandPipeline: (Command) => Unit = {
        case x => commandPL(x)
      }
    }
  }
}
