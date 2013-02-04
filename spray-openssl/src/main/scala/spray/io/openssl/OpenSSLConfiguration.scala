package spray.io.openssl

import spray.io.{PipelineContext, ClientSSLEngineProvider}
import java.security.cert.Certificate
import api._

trait OpenSSLConfigurator {
  /**
   * Set a list of ciphers to support. See `man ciphers` for the syntax
   * of the string to pass in here.
   */
  def acceptCiphers(cipherDesc: String): this.type
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
  def apply(): OpenSSLClientConfigurator =
    new OpenSSLClientConfigurator {
      var ciphers: Option[String] = None
      var useDefaultVerifyPaths = true
      var verify = true
      var certificates: List[X509Certificate] = Nil

      def build(): ClientSSLEngineProvider = {
        val ctx = SSLCtx.create(OpenSSL.SSLv23_method)
        ctx.setMode(SSL.SSL_MODE_RELEASE_BUFFERS)
        ctx.setOptions(SSL.SSL_OP_NO_COMPRESSION)

        if (useDefaultVerifyPaths) ctx.setDefaultVerifyPaths()
        ctx.setVerify(if (verify) 1 else 0)

        ciphers.foreach { cipherDesc =>
          OpenSSL.checkResult(ctx.setCipherList(DirectBuffer.forCString(cipherDesc)))
        }

        val certStore = ctx.getCertificateStore
        certificates.foreach(certStore.addCertificate)

        def sslFactory(pipeCtx: PipelineContext): SSL = ctx.newSSL()

        ClientSSLEngineProvider(OpenSslSupport(sslFactory, sslEnabled = _ => true, client = true) _)
      }

      def acceptCiphers(cipherDesc: String): this.type =
        andReturnSelf { ciphers = Some(cipherDesc) }

      def acceptServerCertificate(certificate: Certificate): this.type =
        andReturnSelf { certificates ::= X509Certificate(certificate) }

      def dontAcceptDefaultVerifyPaths(): this.type = throw new UnsupportedOperationException("nyi")

      def setSessionHandler(handler: SessionHandler): this.type = throw new UnsupportedOperationException("nyi")

      // why would you do THAT? I don't know. Still it's possible...
      def disableVerification(): this.type = andReturnSelf(verify = false)

      def andReturnSelf(f: => Unit): this.type = {
        f
        this
      }
    }
}
