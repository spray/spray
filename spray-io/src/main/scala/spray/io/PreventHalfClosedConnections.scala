package spray.io

import akka.io.Tcp

/**
 * A pipeline stage that prevents half-closed connections by actively closing this
 * side of the connection when a Tcp.PeerClosed command was received.
 *
 * It is only activated when SslTlsSupport is disabled because
 * SslTlsSupport has the same closing semantics as this stage.
 */
object PreventHalfClosedConnections {
  def apply(sslEnabled: Boolean): RawPipelineStage[SslTlsContext] =
    new OptionalPipelineStage[SslTlsContext] {
      def enabled(context: SslTlsContext): Boolean = !isSslTlsSupportEnabled(context)
      def isSslTlsSupportEnabled(context: SslTlsContext) =
        sslEnabled && context.sslEngine.isDefined

      def applyIfEnabled(context: SslTlsContext,
                         commandPL: CPL,
                         eventPL: EPL): Pipelines = new Pipelines with DynamicEventPipeline {
        def initialEventPipeline = connected
        def commandPipeline = commandPL

        def connected: EPL = {
          case Tcp.PeerClosed ⇒
            commandPL(Tcp.ConfirmedClose)
            eventPipeline.become(closingOurSide)

          case ev ⇒ eventPL(ev)
        }
        def closingOurSide: EPL = {
          case _: Tcp.ConnectionClosed ⇒ eventPL(Tcp.PeerClosed)
          case ev                      ⇒ eventPL(ev)
        }
      }
    }
}
