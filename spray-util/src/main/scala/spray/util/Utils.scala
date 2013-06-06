/*
 * Copyright (C) 2011-2013 spray.io
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

package spray.util

import java.nio.channels.ServerSocketChannel
import java.net.{ InetAddress, InetSocketAddress }
import com.typesafe.config.{ ConfigFactory, Config }
import scala.collection.JavaConverters._
import akka.actor._

object Utils {

  def actorSystemNameFrom(clazz: Class[_]) =
    clazz.getName.replace('.', '-').filter(_ != '$')

  def temporaryServerAddress(interface: String = "127.0.0.1"): InetSocketAddress = {
    val serverSocket = ServerSocketChannel.open()
    try {
      serverSocket.socket.bind(new InetSocketAddress(interface, 0))
      val port = serverSocket.socket.getLocalPort
      new InetSocketAddress(interface, port)
    } finally serverSocket.close()
  }

  def temporaryServerHostnameAndPort(interface: String = "127.0.0.1"): (String, Int) = {
    val socketAddress = temporaryServerAddress(interface)
    socketAddress.getHostName -> socketAddress.getPort
  }

  private[this] var eventStreamLogger: ActorRef = _
  def installEventStreamLoggerFor(channel: Class[_])(implicit system: ActorSystem): Unit = {
    synchronized {
      if (eventStreamLogger == null)
        eventStreamLogger = system.actorOf(Props(new Actor with SprayActorLogging {
          def receive = { case x ⇒ log.warning(x.toString) }
        }), name = "event-stream-logger")
    }
    system.eventStream.subscribe(eventStreamLogger, channel)
  }
  def installEventStreamLoggerFor[T](implicit ct: ClassManifest[T], system: ActorSystem): Unit =
    installEventStreamLoggerFor(classManifest[T].erasure)

  def installDebuggingEventStreamLoggers()(implicit refFactory: ActorRefFactory): Unit = {
    implicit val system = actorSystem
    installEventStreamLoggerFor[DeadLetter]
    installEventStreamLoggerFor[UnhandledMessage]
  }

  lazy val sprayConfigAdditions: Config = ConfigFactory.parseMap {
    Map("spray.hostname" -> tryOrElse(InetAddress.getLocalHost.getHostName, _ ⇒ "")).asJava
  }

  def mapToConfig(map: Map[String, Any]): Config = ConfigFactory.parseMap(map.asJava)

  def humanReadableByteCount(bytes: Long, si: Boolean): String = {
    val unit = if (si) 1000 else 1024
    if (bytes >= unit) {
      val exp = (math.log(bytes) / math.log(unit)).toInt
      val pre = if (si) "kMGTPE".charAt(exp - 1).toString else "KMGTPE".charAt(exp - 1).toString + 'i'
      "%.1f %sB" format (bytes / math.pow(unit, exp), pre)
    } else bytes.toString + "  B"
  }
}
