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

package spray.testkit

import java.net.InetSocketAddress
import com.typesafe.config.{ ConfigFactory, Config }
import org.scalatest.Suite
import akka.testkit.{ TestActorRef, TestProbe }
import akka.actor._
import spray.util._
import spray.io._
import akka.event.{ Logging, LoggingAdapter }
import akka.util.ByteString

trait RawPipelineStageTest { test ⇒
  type Context <: PipelineContext

  final val testConf: Config = ConfigFactory.parseString("""
    akka {
      event-handlers = ["akka.testkit.TestEventListener"]
      loglevel = "WARNING"
      stdout-loglevel = "WARNING"
    }""")

  lazy val config: Config = ConfigFactory.empty()
  implicit lazy val system: ActorSystem = actorSystem(config)
  val log: LoggingAdapter = Logging(system, getClass)

  def actorSystem(config: String): ActorSystem = actorSystem(ConfigFactory parseString config)
  def actorSystem(config: Map[String, _]): ActorSystem = actorSystem(Utils.mapToConfig(config))
  def actorSystem(config: Config): ActorSystem = ActorSystem("PipelineStageTest", config withFallback testConf)

  def cleanUp(): Unit = { system.shutdown() }

  def remoteHostName = "example.com"
  def remoteHostPost = 8080
  def localHostName = "127.0.0.1"
  def localHostPost = 32598

  def createPipelineContext(actorContext: ActorContext, remoteAddress: InetSocketAddress,
                            localAddress: InetSocketAddress, log: LoggingAdapter): Context = ???

  class BaseFixture(stage: RawPipelineStage[Context],
                    remoteAddress: InetSocketAddress = new InetSocketAddress(remoteHostName, remoteHostPost),
                    localAddress: InetSocketAddress = new InetSocketAddress(localHostName, localHostPost)) { fixture ⇒

    val commands = TestProbe()
    val events = TestProbe()

    lazy val connectionActor = TestActorRef(new ConnectionHandlerTestActor)

    lazy val pipelineContext = createPipelineContext(connectionActor.underlyingActor.theContext, remoteAddress,
      localAddress, log)

    lazy val pipelines: Pipelines = stage(pipelineContext, commands.ref ! _, events.ref ! _)

    // override if you need per-Fixture PipelineContexts rather than per-test instances
    def createPipelineContext(actorContext: ActorContext, remoteAddress: InetSocketAddress,
                              localAddress: InetSocketAddress, log: LoggingAdapter): Context =
      test.createPipelineContext(actorContext, remoteAddress, localAddress, log)

    class ConnectionHandlerTestActor extends Actor {
      def theContext = context // make publicly visible
      def receive: Receive = {
        case x: Command        ⇒ pipelines.commandPipeline(x)
        case x: Event          ⇒ pipelines.eventPipeline(x)
        case Terminated(actor) ⇒ pipelines.eventPipeline(Pipeline.ActorDeath(actor))
      }
    }
  }

  object StringBytes {
    def unapply(data: ByteString): Option[String] = Some(data.utf8String)
  }

  def probeAndRef() = {
    val probe = TestProbe()
    probe -> probe.ref
  }
}

trait DefaultPipelineStageTest extends RawPipelineStageTest {
  type Context = PipelineContext
  override def createPipelineContext(actorContext: ActorContext, remoteAddress: InetSocketAddress,
                                     localAddress: InetSocketAddress, log: LoggingAdapter) =
    PipelineContext(actorContext, remoteAddress, localAddress, log)
}

trait RawScalatestPipelineStageTest extends RawPipelineStageTest with ScalatestInterface { _: Suite ⇒
  type Fixture = BaseFixture
}

trait ScalatestPipelineStageTest extends RawScalatestPipelineStageTest with DefaultPipelineStageTest { _: Suite ⇒ }

trait RawSpecs2PipelineStageTest extends RawPipelineStageTest with Specs2Interface {
  class Fixture(stage: RawPipelineStage[Context],
                remoteAddress: InetSocketAddress = new InetSocketAddress(remoteHostName, remoteHostPost),
                localAddress: InetSocketAddress = new InetSocketAddress(localHostName, localHostPost))
      extends BaseFixture(stage, remoteAddress, localAddress) with org.specs2.specification.Scope
}

trait Specs2PipelineStageTest extends RawSpecs2PipelineStageTest with DefaultPipelineStageTest