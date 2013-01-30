package spray.io.openssl

import org.specs2.mutable.Specification
import spray.io._
import akka.actor._
import spray.io.IOBridge.Key
import javax.net.ssl._
import java.security.{SecureRandom, KeyStore}
import akka.testkit.{TestProbe, TestKit}
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import annotation.tailrec

class OpenSslSpecs extends TestKit(ActorSystem()) with Specification {
  val context = createSslContext("/ssl-test-keystore.jks", "")
  System.setProperty("javax.net.debug", "ssl,handshake")

  "OpenSslSupport" should {
    "gracefully close connection" in pending

    "support client mode" in {
      "run a full cycle" in {
        val engine = context.createSSLEngine()
        engine.setUseClientMode(false)

        val javaSslServer = StageTestSetup(SslTlsSupport(_ => engine, system.log))
        val openSslClient = StageTestSetup(OpenSslSupport(client = true)(system.log))

        def transferToServer(): Seq[SSLMessage] =
          openSslClient transferSSLMessageTo javaSslServer

        def transferToClient(): Seq[SSLMessage] =
          javaSslServer transferSSLMessageTo openSslClient

        // make sure handshaking has not started yet
        engine.getHandshakeStatus must be_==(SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING)

        // client hello
        transferToServer() must be_==(Seq(HandshakeMessage(ClientHello)))

        // server hello
        transferToClient() must be_==(Seq(HandshakeMessage(ServerHello)))

        // make sure handshaking is not finished on the java (server) side yet
        engine.getHandshakeStatus must be_==(SSLEngineResult.HandshakeStatus.NEED_UNWRAP)

        // client key exchange
        transferToServer() must be_==(
          Seq(
            HandshakeMessage(ClientKeyExchange),
            ChangeCipherSpec,
            HandshakeMessage(EncryptedHandshakeMessage)))

        // change cipher spec
        transferToClient() must be_==(Seq(ChangeCipherSpec))

        // for some reason the java ssl implementation transports these in two packets
        // encrypted handshake
        transferToClient() must be_==(Seq(HandshakeMessage(EncryptedHandshakeMessage)))

        // make sure handshaking is now finished on server
        engine.getHandshakeStatus must be_==(SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING)

        // send something through the pipe from client to server
        openSslClient.sendData("testdata".getBytes)

        // transfer encrypted data to server
        transferToServer() must be_==(Seq(EncryptedData))

        javaSslServer.commander.expectMsgType[IOPeer.Received].buffer.remaining() must be_==(8)

        // send something through the pipe from server to client
        javaSslServer.sendData("test".getBytes)

        // transfer encrypted data to client
        transferToClient() must be_==(Seq(EncryptedData))
        openSslClient.commander.expectMsgType[IOPeer.Received].buffer.remaining() must be_==(4)
      }
      "make sure big buffers are written properly" in {

      }
      "configure ciphers" in pending
      "allow extraction of sessions" in pending
      "reuse old session if requested" in pending
      "disable verification" in pending
    }
    "support server mode" in {
      "run a full message cycle" in pending
      "configure ciphers" in pending
      "configure key and authority" in pending
    }
  }

  step { system.shutdown() }

  case class StageTestSetup(commander: TestProbe, stageActor: ActorRef, stateMachine: SimpleTlsStateMachine) {
    def transferSSLMessageTo(other: StageTestSetup): Seq[SSLMessage] = {
      val data = commander.expectMsgType[IOPeer.Send]
      val msgs = stateMachine.analyzePackage(data.buffers.head)
      other.commander.send(other.stageActor, reverse(data))
      msgs
    }

    def sendData(bytes: Array[Byte]): Unit =
      commander.send(stageActor, IOPeer.Send(ByteBuffer.wrap(bytes)))
  }
  object StageTestSetup {
    def apply(stage: PipelineStage): StageTestSetup = {
      val commander = TestProbe()
      val actor = createStageActor(stage, commander.ref)
      StageTestSetup(commander, actor, new SimpleTlsStateMachine)
    }
  }

  class SimpleTlsStateMachine {
    var isEncrypted = false

    /** A mini package analyzer for TLS v1.0/1.1 records */
    def analyzePackage(buffer: ByteBuffer): Seq[SSLMessage]  = {
      println("Total size: %d" format buffer.remaining())
      @tailrec def analyzeNext(isEncrypted: Boolean, result: Seq[SSLMessage]): (Boolean, Seq[SSLMessage]) = {
        val tpe = buffer.get()
        val protocol = buffer.getShort()

        require(protocol == 0x302 || protocol == 0x301,
          "supporting only tls v1.1 or v1.0 currently but proto was 0x%x" format protocol)

        val length = buffer.getShort()

        val afterPackage = buffer.position() + length

        val (isNowEncrypted, newOne) = tpe match {
          case 22 =>
            val tpe =
              buffer.get() match {
                case _ if isEncrypted => EncryptedHandshakeMessage
                case 1 => ClientHello
                case 2 => ServerHello
                case 11 => Certificate
                case 16 => ClientKeyExchange
              }
            (isEncrypted, HandshakeMessage(tpe))
          case 20 => (true, ChangeCipherSpec)
          case 23 => (isEncrypted, EncryptedData)
        }

        buffer.position(afterPackage)

        if (buffer.remaining() > 0) analyzeNext(isNowEncrypted, result :+ newOne)
        else (isNowEncrypted, result :+ newOne)
      }
      val pos = buffer.position()
      try {
        val (isNowEncrypted, result) = analyzeNext(isEncrypted, Nil)
        isEncrypted = isNowEncrypted
        result
      } finally buffer.position(pos)
    }
  }

  sealed trait HandshakeMessageType
  case object ClientHello extends HandshakeMessageType
  case object ServerHello extends HandshakeMessageType
  case object Certificate extends HandshakeMessageType
  case object ClientKeyExchange extends HandshakeMessageType
  case object EncryptedHandshakeMessage extends HandshakeMessageType

  sealed trait SSLMessage
  case class HandshakeMessage(tpe: HandshakeMessageType) extends SSLMessage
  case object ChangeCipherSpec extends SSLMessage
  case object EncryptedData extends SSLMessage


  def createSslContext(keyStoreResource: String, password: String): SSLContext = {
    val keyStore = KeyStore.getInstance("jks")
    val res = getClass.getResourceAsStream(keyStoreResource)
    require(res != null)
    keyStore.load(res, password.toCharArray)
    val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(keyStore, password.toCharArray)
    val trustManagerFactory = TrustManagerFactory.getInstance("SunX509")
    trustManagerFactory.init(keyStore)
    val context = SSLContext.getInstance("SSL")
    context.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, new SecureRandom)
    context
  }

  def reverse(command: IOPeer.Send): IOPeer.Received = command match {
    case IOPeer.Send(Seq(one), _) => IOPeer.Received(null, one)
  }
  def reverse(event: IOPeer.Received): IOPeer.Send =
    IOPeer.Send(event.buffer, None)

  def createStageActor(stage: PipelineStage, handler: ActorRef): ActorRef = {
    def unsupported = throw new UnsupportedOperationException

    class StageActor extends Actor with ActorLogging { actor =>
      def receive = {
        case cmd: Command => stageCommandPL(cmd)
        case event: Event => stageEventPL(event)
      }

      val (stageCommandPL, stageEventPL) = {
        val pipes =
          stage.build(new PipelineContext {
            def log = actor.log

            def connectionActorContext: ActorContext = context
            val connection: Connection = new Connection {
              def commander: ActorRef = unsupported
              def key: Key = unsupported
              def handler: ActorRef = unsupported
              def ioBridge: ActorRef = unsupported
              def tag: Any = null

              override def remoteAddress: InetSocketAddress = unsupported
            }
          }, handler !, handler !)
        (pipes.commandPipeline, pipes.eventPipeline)
      }
    }

    system.actorOf(Props(new StageActor))
  }
}
