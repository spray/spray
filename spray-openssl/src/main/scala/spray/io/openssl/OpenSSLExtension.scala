package spray.io.openssl

import akka.actor.{ExtendedActorSystem, ExtensionKey, Extension}
import com.typesafe.config.Config

object OpenSSLExtension extends ExtensionKey[OpenSSLExtension]

class OpenSSLExtension(system: ExtendedActorSystem) extends Extension {
  val Settings = new Settings(system.settings.config.getConfig("spray.io.openssl"))
  class Settings private[OpenSSLExtension] (config: Config) {
    import config._

    val keepNativeSessions = getBoolean("keep-native-sessions")
  }

  def newClientConfigurator(): OpenSSLClientConfigurator = OpenSSLClientConfigurator(this)
}

