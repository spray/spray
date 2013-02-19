package spray.io.openssl

import org.specs2.mutable.Specification
import spray.io._
import akka.actor._
import openssl.OpenSslSupport.DirectBufferPool
import spray.io.IOBridge.Key
import javax.net.ssl._
import java.security.{SecureRandom, KeyStore}
import akka.testkit.{TestProbe, TestKit}
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import annotation.tailrec
import spray.util.ConnectionCloseReasons.PeerClosed
import spray.io.SslTlsSupport.Enabling

class OpenSslSpecs extends TestKit(ActorSystem()) with Specification {
  val keyStore = loadKeyStore("/ssl-test-keystore.jks", "")
  val context = createSslContext("")

  "OpenSslSupport" should {
    "gracefully close connection" in pending

    "support client mode" in {
      "run a full cycle" in withEstablishedConnection() { setup =>
        import setup._

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
      "make sure big buffers are written properly" in withEstablishedConnection() { setup =>
        import setup._

        val totalSize = 500000
        openSslClient.sendData(new Array[Byte](totalSize))

        @tailrec def receiveAll(remaining: Int): Unit =
          if (remaining > 0) {
            transferToServer() must be_==(Seq(EncryptedData))
            val received = javaSslServer.commander.expectMsgType[IOPeer.Received]
            //println("Got data "+received.buffer.remaining())
            receiveAll(remaining - received.buffer.remaining())
          }
        receiveAll(totalSize)
      }
      "don't crash when data is sent before handshake is finished" in {
        val engine = context.createSSLEngine()
        engine.setUseClientMode(false)

        val javaSslServer = StageTestSetup(SslTlsSupport(_ => engine, system.log))
        val openSslClient = StageTestSetup(defaultConfig(newConfigurator()).build())

        val setup = EstablishedConnectionSetup(javaSslServer, openSslClient)
        import setup._

        val data = (0 until 100).map(_.toByte)
        openSslClient.sendData(data.toArray)
        runHandshake(setup)

        // transfer encrypted data to server
        transferToServer() must be_==(Seq(EncryptedData))
        val buf = new Array[Byte](100)
        val recvd = javaSslServer.commander.expectMsgType[IOPeer.Received]
        recvd.buffer.remaining() must be_==(100)
        recvd.buffer.get(buf)
        buf.toSeq must be_==(data)
      }
      "configure ciphers" in pending
      "allow caching of sessions" in {
        // a stupid session cache which doesn't save the session per remote address / hostname

        @volatile var session: Option[Session] = None

        val handler = new SessionHandler {
          def provideSession(ctx: PipelineContext): Option[Session] = session
          def incomingSession(ctx: PipelineContext, s: Session): Unit = session = Some(s)
        }

        def run(handshake: EstablishedConnectionSetup => Unit): Unit = {
          val stage =
            defaultConfig(newConfigurator())
              .setSessionHandler(handler)
              .build()

          val engine = context.createSSLEngine()
          engine.setUseClientMode(false)

          val javaSslServer = StageTestSetup(SslTlsSupport(_ => engine, system.log))
          val openSslClient = StageTestSetup(stage)

          val setup = EstablishedConnectionSetup(javaSslServer, openSslClient)
          import setup._

          handshake(setup)
        }

        run(runHandshake)
        // provoke segfault if some callbacks would be collected in between
        System.gc()

        session must beSome

        run(runAbbreviatedHandshake)
        ok
      }
      "disable verification" in pending
    }
    "support server mode" in {
      "run a full message cycle" in pending
      "configure ciphers" in pending
      "configure key and authority" in pending
    }
  }

  step {
    system.shutdown()
    system.awaitTermination()

    api.OpenSSL.shutdown()
  }

  def withEstablishedConnection[T](extraConfig: OpenSSLClientConfigurator => OpenSSLClientConfigurator = defaultConfig)(body: EstablishedConnectionSetup => T): T = {
    val engine = context.createSSLEngine()
    engine.setUseClientMode(false)

    val javaSslServer = StageTestSetup(SslTlsSupport(_ => engine, system.log))
    val openSslClient = StageTestSetup(extraConfig(newConfigurator()).build())

    val setup = EstablishedConnectionSetup(javaSslServer, openSslClient)
    runHandshake(setup)

    // make sure handshaking is now finished on server
    engine.getHandshakeStatus must be_==(SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING)

    body(setup)
  }

  def defaultConfig: OpenSSLClientConfigurator => OpenSSLClientConfigurator =
    _.acceptServerCertificate(keyStore.getCertificate("spray team"))
     .acceptCiphers("RSA")

  def runHandshake(setup: EstablishedConnectionSetup): Unit = {
    import setup._

    // make sure handshaking has not started yet
    //engine.getHandshakeStatus must be_==(SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING)

    // client hello
    transferToServer() must be_==(Seq(HandshakeMessage(ClientHello)))

    // server hello
    transferToClient() must be_==(Seq(HandshakeMessage(ServerHello)))

    // make sure handshaking is not finished on the java (server) side yet
    //engine.getHandshakeStatus must be_==(SSLEngineResult.HandshakeStatus.NEED_UNWRAP)

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

    setup.javaSslServer.expectProcessingFinished()
    setup.openSslClient.expectProcessingFinished()
  }
  def runAbbreviatedHandshake(setup: EstablishedConnectionSetup): Unit = {
    import setup._

    // make sure handshaking has not started yet
    //engine.getHandshakeStatus must be_==(SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING)

    // client hello
    transferToServer() must be_==(Seq(HandshakeMessage(ClientHello)))

    // server hello
    transferToClient() must be_==(Seq(HandshakeMessage(ServerHello)))
    transferToClient() must be_==(Seq(ChangeCipherSpec))
    transferToClient() must be_==(Seq(HandshakeMessage(EncryptedHandshakeMessage)))

    // client key exchange
    transferToServer() must be_==(
      Seq(
        ChangeCipherSpec,
        HandshakeMessage(EncryptedHandshakeMessage)))

    setup.javaSslServer.expectProcessingFinished()
    setup.openSslClient.expectProcessingFinished()
  }

  case class EstablishedConnectionSetup(javaSslServer: StageTestSetup, openSslClient: StageTestSetup) {
    def transferToServer(): Seq[SSLMessage] =
      openSslClient transferSSLMessageTo javaSslServer

    def transferToClient(): Seq[SSLMessage] =
      javaSslServer transferSSLMessageTo openSslClient
  }

  case class StageTestSetup(commander: TestProbe, stageActor: ActorRef, stateMachine: SimpleTlsStateMachine) {
    def transferSSLMessageTo(other: StageTestSetup): Seq[SSLMessage] = {
      val data = commander.expectMsgType[IOPeer.Send]
      val msgs = stateMachine.analyzePackage(data.buffers.head)
      other.commander.send(other.stageActor, reverse(data))
      msgs
    }

    def sendData(bytes: Array[Byte]): Unit =
      commander.send(stageActor, IOPeer.Send(ByteBuffer.wrap(bytes)))

    /** Checks that no messages are processed any more */
    def expectProcessingFinished(): Unit = {
      val probe = TestProbe()
      probe.send(stageActor, Ping)
      probe.expectMsg(Pong)
    }
  }
  object StageTestSetup {
    def apply(stage: PipelineStage): StageTestSetup = {
      val commander = TestProbe()
      val actor = createStageActor(stage, commander.ref)
      StageTestSetup(commander, actor, new SimpleTlsStateMachine)
    }
    def apply(provider: StageProvider): StageTestSetup =
      apply(provider.createStage(system.log))
  }

  class SimpleTlsStateMachine {
    var isEncrypted = false

    /** A mini package analyzer for TLS v1.0/1.1 records */
    def analyzePackage(buffer: ByteBuffer): Seq[SSLMessage]  = {
      //println("Total size: %d" format buffer.remaining())
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

  def loadKeyStore(keyStoreResource: String, password: String): KeyStore = {
    val keyStore = KeyStore.getInstance("jks")
    val res = getClass.getResourceAsStream(keyStoreResource)
    require(res != null)
    keyStore.load(res, password.toCharArray)
    keyStore
  }

  def createSslContext(password: String): SSLContext = {
    val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(keyStore, password.toCharArray)
    val trustManagerFactory = TrustManagerFactory.getInstance("SunX509")
    trustManagerFactory.init(keyStore)
    val context = SSLContext.getInstance("SSL")
    context.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, new SecureRandom)
    context
  }

  def newConfigurator() = OpenSSLExtension(system).newClientConfigurator()

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
        case Ping => sender ! Pong
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
              def tag: Any = new Enabling {
                def encrypt(ctx: PipelineContext): Boolean = true
              }

              override def remoteAddress: InetSocketAddress = unsupported
            }
          }, handler !, handler !)
        (pipes.commandPipeline, pipes.eventPipeline)
      }

      override def postStop() {
        stageEventPL(IOPeer.Closed(null, PeerClosed))
      }
    }

    system.actorOf(Props(new StageActor))
  }
  object Ping
  object Pong
}
