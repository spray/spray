/*
 * Copyright (C) 2011-2012 spray.cc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cc.spray.site

import akka.actor.{Props, ActorSystem}
import cc.spray.can.server.HttpServer
import cc.spray.io._
import cc.spray.can.spdy.server.SpdyHttpServer
import javax.net.ssl.{TrustManagerFactory, KeyManagerFactory, SSLContext}
import java.security.{SecureRandom, KeyStore}
import java.io.FileOutputStream
import org.eclipse.jetty.npn.NextProtoNego
import org.eclipse.jetty.npn.NextProtoNego.ServerProvider
import java.util


object Boot extends App {
  // we need an ActorSystem to host our application in
  val system = ActorSystem("demo")

  // every spray-can HttpServer (and HttpClient) needs an IOBridge for low-level network IO
  // (but several servers and/or clients can share one)
  val ioBridge = new IOBridge(system).start()

  // create and start our service actor
  val service = system.actorOf(Props[SiteServiceActor], "site-service")

  // create and start the spray-can HttpServer, telling it that
  // we want requests to be handled by our singleton service actor
  val httpServer = system.actorOf(
    Props(new SpdyHttpServer(ioBridge, SingletonHandler(service))(sslEngineProvider)),
    name = "http-server"
  )

  // a running HttpServer can be bound, unbound and rebound
  // initially to need to tell it where to bind to
  httpServer ! HttpServer.Bind("localhost", 8081)

  // finally we drop the main thread but hook the shutdown of
  // our IOBridge into the shutdown of the applications ActorSystem
  system.registerOnTermination {
    ioBridge.stop()
  }

  /*object MyServerProvider extends ServerProvider {
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
  }*/
  NextProtoNego.debug = true
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
    ServerSSLEngineProvider.fromFunc { ctx =>
      val engine = defa(ctx)
      engine.setEnabledCipherSuites(Array("TLS_RSA_WITH_AES_256_CBC_SHA"))
      engine.setEnabledProtocols(Array("SSLv3", "TLSv1"))
      //if (ctx.handle.localAddress.getPort == 8081)
      //  NextProtoNego.put(engine, MyServerProvider)
      engine
    }
  }
}