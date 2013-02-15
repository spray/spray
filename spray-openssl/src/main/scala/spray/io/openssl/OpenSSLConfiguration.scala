package spray.io.openssl

import api.LibSSL.NewSessionCB
import spray.io.{PipelineContext, ClientSSLEngineProvider}
import java.security.cert.Certificate
import api._

trait OpenSSLConfigurator {
  /**
   * Set a list of ciphers to support. See `man ciphers` for the syntax
   * of the string to pass in here.
   */
  def acceptCiphers(cipherDesc: String): this.type

  /**
   * Disable TLS 1.1 and 1.2. This is because of
   * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=7031830
   */
  def disableTls1_1And1_2(): this.type
}

trait SessionHandler {
  def incomingSession(ctx: PipelineContext, session: Session): Unit
  def provideSession(ctx: PipelineContext): Option[Session]
}

trait Session {
  def asBytes: Array[Byte]
}
object Session {
  def apply(bytes: Array[Byte]): Session =
    new Session {
      def asBytes: Array[Byte] = bytes
    }
}

trait OpenSSLClientConfigurator extends OpenSSLConfigurator {
  def build(): ClientSSLEngineProvider

  /**
   * Add a server certificate which should be accepted.
   */
  def acceptServerCertificate(certificate: Certificate): this.type

  /**
   * Disables the default OS-defined verification paths to load
   * root CAs from.
   */
  def dontAcceptDefaultVerifyPaths(): this.type

  /**
   * Is called whenever a session was established. Allows client code to save the
   * session for later. Make sure the handler is thread-safe because it may be called
   * concurrently.
   */
  def setSessionHandler(handler: SessionHandler): this.type

  // why would you do THAT? I don't know. Still it's possible...
  def disableVerification(): this.type

}
object OpenSSLClientConfigurator {
  // we save a reference of the associated pipelineCtx into the native data structure
  // and reserve a slot here for this purpose
  val pipelineContextSlot = SSL.createExDataSlot[PipelineContext]()
  val sessionHandlerSlot = SSLCtx.createExDataSlot[(SSL, SSL_SESSION) => Unit]()

  val sessionCB = new NewSessionCB {
    def apply(ssl: SSL, session: SSL_SESSION) {
      val handler = ssl.getCtx(sessionHandlerSlot)
      if (handler != null) handler(ssl, session)
    }
  }

  trait BaseOpenSSLConfigurator {
    var ciphers: Option[String] = None
    var `disable Tls v1.1 and v1.2`: Boolean = false

    def createCtx: SSLCtx = {
      val ctx = SSLCtx.create(OpenSSL.SSLv23_method)
      ctx.setMode(SSL.SSL_MODE_RELEASE_BUFFERS)
      ctx.setOptions(SSL.SSL_OP_NO_COMPRESSION | // because it needs huge buffers
                     SSL.SSL_OP_NO_SSLv2)         // because it's unsafe

      if (`disable Tls v1.1 and v1.2`) ctx.setOptions(SSL.SSL_OP_NO_TLSv1_1 | SSL.SSL_OP_NO_TLSv1_2)

      ciphers.foreach { cipherDesc =>
        ctx.setCipherList(DirectBuffer.forCString(cipherDesc))
      }

      ctx
    }
  }

  def apply(ext: OpenSSLExtension): OpenSSLClientConfigurator =
    new OpenSSLClientConfigurator with BaseOpenSSLConfigurator {
      var useDefaultVerifyPaths = true
      var verify = true
      var certificates: List[X509Certificate] = Nil
      var sessionHandler: Option[SessionHandler] = None
      val keepNativeSessions = ext.Settings.keepNativeSessions

      def build(): ClientSSLEngineProvider = {
        val ctx = createCtx

        if (useDefaultVerifyPaths) ctx.setDefaultVerifyPaths()
        ctx.setVerify(if (verify) 1 else 0)

        val certStore = ctx.getCertificateStore
        certificates.foreach(certStore.addCertificate)

        sessionHandler.foreach { handler =>
          // enable session caching but disable internal caching and, instead, ...
          ctx.setSessionCacheMode(SSLCtx.SSL_SESS_CACHE_CLIENT | SSLCtx.SSL_SESS_CACHE_NO_INTERNAL)

          ctx(sessionHandlerSlot) = (ssl: SSL, session: SSL_SESSION) => {
            val bytes = session.toBytes

            def createSession() =
              if (keepNativeSessions)
                new SimpleSession(bytes) with InMemorySession {
                  // FIXME: when do we release native sessions?
                  val sessCopy = ssl.get1Session()

                  val creationCtx = ctx

                  def get: SSL_SESSION = sessCopy
                  def belongsTo(ctx: SSLCtx): Boolean = ctx == creationCtx
                }
              else new SimpleSession(bytes)

            handler.incomingSession(ssl(pipelineContextSlot), createSession())
          }

          // ... register a callback to do it on our side
          ctx.setNewSessionCallback(sessionCB)
        }

        def convertSession(session: Session): SSL_SESSION = session match {
          case s: InMemorySession if s belongsTo ctx => s.get
          case s: Session => SSL_SESSION.fromBytes(s.asBytes)
        }

        def sslFactory(pipeCtx: PipelineContext): SSL = {
          val ssl = ctx.newSSL()

          ssl(pipelineContextSlot) = pipeCtx

          for {
            handler <- sessionHandler
            session <- handler.provideSession(pipeCtx)
          } ssl.setSession(convertSession(session))

          ssl
        }

        ClientSSLEngineProvider(OpenSslSupport(sslFactory, sslEnabled = _ => true, client = true) _)
      }

      def acceptCiphers(cipherDesc: String): this.type =
        andReturnSelf { ciphers = Some(cipherDesc) }

      def disableTls1_1And1_2(): this.type =
        andReturnSelf { `disable Tls v1.1 and v1.2` = true }

      def acceptServerCertificate(certificate: Certificate): this.type =
        andReturnSelf { certificates ::= X509Certificate(certificate) }

      def dontAcceptDefaultVerifyPaths(): this.type =
        andReturnSelf { useDefaultVerifyPaths = false }

      def setSessionHandler(handler: SessionHandler): this.type =
        andReturnSelf { sessionHandler = Some(handler) }

      // why would you do THAT? I don't know. Still it's possible...
      def disableVerification(): this.type = andReturnSelf(verify = false)

      def andReturnSelf(f: => Unit): this.type = {
        f
        this
      }
    }

  class SimpleSession(val asBytes: Array[Byte]) extends Session

  trait InMemorySession {
    def belongsTo(ctx: SSLCtx): Boolean
    def get: SSL_SESSION
  }
}
