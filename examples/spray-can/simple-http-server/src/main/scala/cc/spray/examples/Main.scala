package cc.spray.examples

import java.security.{SecureRandom, KeyStore}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import akka.actor._
import cc.spray.io.IOBridge
import cc.spray.io.pipelining.{SingletonHandler, ServerSSLEngineProvider}
import cc.spray.can.server.{ServerSettings, HttpServer}
import java.io.FileOutputStream
import cc.spray.can.spdy.server.SpdyHttpServer
import com.typesafe.config.ConfigFactory
import org.eclipse.jetty.npn.NextProtoNego.ServerProvider
import java.util
import org.eclipse.jetty.npn.NextProtoNego

object Reflector {
  def create(system: ActorSystem, ioBridge: IOBridge)(ssl: ServerSSLEngineProvider): ActorRef = {
    val reflectHandler = system.actorOf(Props[ReflectService])

    val mySettings = new ServerSettings(ConfigFactory.load()){
      //override val SSLEncryption: Boolean = false
    }

    system.actorOf(
      props = Props(new HttpServer(ioBridge, SingletonHandler(reflectHandler), settings = mySettings)(ssl)),
      name = "reflect-http-server"
    )
  }
}

object Main extends App {
  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("simple-http-server")

  // every spray-can HttpServer (and HttpClient) needs an IOBridge for low-level network IO
  // (but several servers and/or clients can share one)
  val ioBridge = new IOBridge(system).start()

  // the handler actor replies to incoming HttpRequests
  val handler = system.actorOf(Props[TestService])

  val classicServer = Reflector.create(system, ioBridge)(sslEngineProvider)

  // create and start the spray-can HttpServer, telling it that we want requests to be
  // handled by our singleton handler
  val spdyServer = system.actorOf(
    props = Props(new SpdyHttpServer(ioBridge, SingletonHandler(handler))),
    name = "spdy-http-server"
  )

  cc.spray.util.logEventStreamOf[DeadLetter]
  cc.spray.util.logEventStreamOf[UnhandledMessage]

  // a running HttpServer can be bound, unbound and rebound
  // initially to need to tell it where to bind to
  classicServer ! HttpServer.Bind("localhost", 8082)

  spdyServer ! HttpServer.Bind("localhost", 8081)

  // finally we drop the main thread but hook the shutdown of
  // our IOBridge into the shutdown of the applications ActorSystem
  system.registerOnTermination {
    ioBridge.stop()
  }

  object MyServerProvider extends ServerProvider {
    def unsupported() {
      println("Unsupported called")
    }

    def protocols(): util.List[String] = {
      println("Protocols called")
      util.Arrays.asList("spdy/2")
    }

    def protocolSelected(protocol: String) {
      println("Protocol "+protocol+" was selected")
    }
  }
  NextProtoNego.debug = true
  /////////////// for SSL support (if enabled in application.conf) ////////////////

  // if there is no SSLContext in scope implicitly the HttpServer uses the default SSLContext,
  // since we want non-default settings in this example we make a custom SSLContext available here
  implicit def sslContext: SSLContext = {
    val keyStoreResource = "/ssl-test-keystore.jks"
    val password = ""

    val keyStore = KeyStore.getInstance("jks")
    keyStore.load(getClass.getResourceAsStream(keyStoreResource), password.toCharArray)
    val pkf = new FileOutputStream("thekey.der")
    import collection.JavaConverters._
    pkf.write(keyStore.getKey("spray team", Array.empty).getEncoded)
    pkf.close()

    val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(keyStore, password.toCharArray)
    val trustManagerFactory = TrustManagerFactory.getInstance("SunX509")
    trustManagerFactory.init(keyStore)
    val context = SSLContext.getInstance("TLS")
    context.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, new SecureRandom)
    context
  }

  // if there is no ServerSSLEngineProvider in scope implicitly the HttpServer uses the default one,
  // since we want to explicitly enable cipher suites and protocols we make a custom ServerSSLEngineProvider
  // available here
  implicit def sslEngineProvider: ServerSSLEngineProvider = {
    val defa = ServerSSLEngineProvider.default
    ServerSSLEngineProvider.fromFunc { address =>
      val engine = defa(address)
      engine.setEnabledCipherSuites(Array("TLS_RSA_WITH_AES_256_CBC_SHA"))
      engine.setEnabledProtocols(Array("SSLv3", "TLSv1"))
      if (address.getPort == 8081)
        NextProtoNego.put(engine, MyServerProvider)
      engine
    }
  }
  //println("Press ENTER for shutdown")
  //Console.readLine()
  //system.shutdown()
}