package cc.spray.can.spdy.pipeline

import cc.spray.io._
import akka.actor.ActorRef

object Frontend {
  def apply(messageHandler: MessageHandler): DoublePipelineStage = new DoublePipelineStage {
    def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines = new Pipelines {
      val handler = messageHandler(context)()

      def eventPipeline: EPL = eventPL

      def commandPipeline: CPL = {
        case Tell(rec, msg, sender) =>
          commandPL(IOPeer.Tell(unpackReceiver(rec), msg, sender))
        case c => commandPL(c)
      }

      def unpackReceiver(rec: Receiver): ActorRef = rec match {
        case MessageHandler => handler
        case Other(receiver) => receiver
      }
    }
  }

  def apply(): DoublePipelineStage = new DoublePipelineStage {
    def build(context: PipelineContext, commandPL: CPL, eventPL: EPL): Pipelines = new Pipelines {
      def commandPipeline: (Command) => Unit = {
        case Tell(Other(rec), msg, sender) =>
          commandPL(IOPeer.Tell(rec, msg, sender))
        case c => commandPL(c)
      }

      def eventPipeline: (Event) => Unit = eventPL
    }
  }

  case class Tell(receiver: Receiver, msg: Any, sender: ActorRef) extends Command

  sealed trait Receiver
  case object MessageHandler extends Receiver
  case class Other(receiver: ActorRef) extends Receiver
}
