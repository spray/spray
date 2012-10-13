package cc.spray.can.spdy
package client

import cc.spray.io._
import cc.spray.can.client._
import cc.spray.http.{HttpRequestPart, HttpMessagePart}
import akka.event.LoggingAdapter
import cc.spray.can.HttpCommand
import pipeline._
import cc.spray.can.server._
import cc.spray.io.TlsNpnSupportedProtocols
import cc.spray.can.HttpCommand
import cc.spray.io.TlsNpnSupportedProtocols
import cc.spray.can.HttpCommand
import cc.spray.io.TlsNpnSupportedProtocols
import cc.spray.can.HttpCommand
import cc.spray.io.TlsNpnSupportedProtocols
import cc.spray.can.HttpCommand

/**
 * Reacts to [[cc.spray.can.HttpClient.Connect]] messages by establishing a connection to the remote host.
 * If the connection has been established successfully a new actor is spun up for the connection, which replies to the
 * sender of the [[cc.spray.can.HttpClient.Connect]] message with a [[cc.spray.can.HttpClient.Connected]] message.
 *
 * You can then send [[cc.spray.can.model.HttpRequestPart]] instances to the connection actor, which are going to be
 * replied to with [[cc.spray.can.model.HttpResponsePart]] messages (or [[akka.actor.Status.Failure]] instances
 * in case of errors).
 */
class SpdyHttpClient(ioBridge: IOBridge, settings: ClientSettings = ClientSettings())
                (implicit sslEngineProvider: ClientSSLEngineProvider) extends IOClient(ioBridge) with ConnectionActors {

  protected val pipeline: PipelineStage = SpdyHttpClient.pipeline(settings, log)

  override protected def createConnectionActor(handle: Handle): IOConnectionActor = new IOConnectionActor(handle) {
    override def receive = super.receive orElse {
      case x: HttpMessagePart with HttpRequestPart => super.receive(HttpCommand(x))
    }
  }
}

object SpdyHttpClient {

  private[can] def pipeline(settings: ClientSettings,
                            log: LoggingAdapter)
                           (implicit sslEngineProvider: ClientSSLEngineProvider): PipelineStage = {
    import settings._

    def protocols =
      TlsNpnSupportedProtocols(
        "http/1.1",
        "http/1.1" -> httpPipeline
        ,"spdy/2"   -> spdy2Pipeline
        )

    def spdy2Pipeline =
      HttpOnSpdy.acceptRequests() >>
      SpdyStreamManager(HttpHelper.unwrapHttpEvent, log, client = true) {
        (ResponseChunkAggregationLimit > 0) ? ResponseChunkAggregation(ResponseChunkAggregationLimit.toInt) >>
        HttpOnSpdy(client = true)
      } >>
      SpdyRendering() >>
      SpdyParsing() >>
      Frontend()

    def httpPipeline =
      ClientFrontend(RequestTimeout, log) >>
      (ResponseChunkAggregationLimit > 0) ? ResponseChunkAggregation(ResponseChunkAggregationLimit.toInt) >>
      ResponseParsing(ParserSettings, log) >>
      RequestRendering(settings) >>
      (settings.IdleTimeout > 0) ? ConnectionTimeouts(IdleTimeout, log)

    //httpPipeline >>
    SSLEncryption ? SslTlsSupport(sslEngineProvider, log, _.handle.tag == SslEnabled, supportedProtocols = Some(protocols)) >>
    (ReapingCycle > 0 && IdleTimeout > 0) ? TickGenerator(ReapingCycle)
  }

  /**
   * Object to be used as `tag` member of `Connect` commands in order to activate SSL encryption on the connection.
   * Note that SSL encryption is only generally available for the HttpClient if the respective config setting is
   * enabled. Using the `SslEnabled` tag while `ssl-encryption` is off in the settings has no effect.
   */
  val SslEnabled = HttpClient.SslEnabled

  ////////////// COMMANDS //////////////
  // HttpRequestParts +
  type Connect = IOClient.Connect;                           val Connect = IOClient.Connect
  type Close = IOClient.Close;                               val Close = IOClient.Close
  type Send = IOClient.Send;                                 val Send = IOClient.Send
  type Tell = IOClient.Tell;                                 val Tell = IOClient.Tell
  type SetIdleTimeout = ConnectionTimeouts.SetIdleTimeout;   val SetIdleTimeout = ConnectionTimeouts.SetIdleTimeout
  type SetRequestTimeout = ClientFrontend.SetRequestTimeout; val SetRequestTimeout = ClientFrontend.SetRequestTimeout

  ////////////// EVENTS //////////////
  // HttpResponseParts +
  val Connected = IOClient.Connected
  type Closed = IOClient.Closed;     val Closed = IOClient.Closed
  type SentOk = IOClient.SentOk;     val SentOk = IOClient.SentOk
  type Received = IOClient.Received; val Received = IOClient.Received

}

