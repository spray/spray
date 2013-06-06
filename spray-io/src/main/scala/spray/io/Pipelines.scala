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

package spray.io

import java.net.InetSocketAddress
import akka.actor.ActorContext
import akka.event.LoggingAdapter
import akka.io.Tcp

//# pipelines
trait Pipelines {
  def commandPipeline: Pipeline[Command]
  def eventPipeline: Pipeline[Event]
}
//#

object Pipelines {
  val Uninitialized = apply(Pipeline.Uninitialized, Pipeline.Uninitialized)

  def apply(commandPL: Pipeline[Command], eventPL: Pipeline[Event]) = new Pipelines {
    val commandPipeline = commandPL
    val eventPipeline = eventPL
  }
}

trait DynamicCommandPipeline { this: Pipelines ⇒
  def initialCommandPipeline: Pipeline[Command]
  private[this] var _cpl: SwitchableCommandPipeline = _
  def commandPipeline: SwitchableCommandPipeline = {
    if (_cpl eq null) _cpl = new SwitchableCommandPipeline(initialCommandPipeline)
    _cpl
  }
  class SwitchableCommandPipeline(private[this] var proxy: Pipeline[Command]) extends Pipeline[Command] {
    def apply(cmd: Command): Unit = proxy(cmd)
    def become(cpl: Pipeline[Command]): Unit = proxy = cpl
  }
}

trait DynamicEventPipeline { this: Pipelines ⇒
  def initialEventPipeline: Pipeline[Event]
  private[this] var _epl: SwitchableEventPipeline = _
  def eventPipeline: SwitchableEventPipeline = {
    if (_epl eq null) _epl = new SwitchableEventPipeline(initialEventPipeline)
    _epl
  }
  class SwitchableEventPipeline(private[this] var proxy: Pipeline[Event]) extends Pipeline[Event] {
    def apply(ev: Event): Unit = proxy(ev)
    def become(epl: Pipeline[Event]): Unit = proxy = epl
  }
}

trait PipelineContext {
  def actorContext: ActorContext
  def remoteAddress: InetSocketAddress
  def localAddress: InetSocketAddress
  def log: LoggingAdapter
}

object PipelineContext {
  def apply(_actorContext: ActorContext, _remoteAddress: InetSocketAddress, _localAddress: InetSocketAddress,
            _log: LoggingAdapter): PipelineContext = new PipelineContext {
    def actorContext: ActorContext = _actorContext
    def remoteAddress: InetSocketAddress = _remoteAddress
    def localAddress: InetSocketAddress = _localAddress
    def log: LoggingAdapter = _log
  }
  implicit def pipelineContext2ActorContext(plc: PipelineContext): ActorContext = plc.actorContext
}

trait RawPipelineStage[-C <: PipelineContext] { left ⇒
  type CPL = Pipeline[Command] // alias for brevity
  type EPL = Pipeline[Event] // alias for brevity

  def apply(context: C, commandPL: CPL, eventPL: EPL): Pipelines

  def >>[R <: C](right: RawPipelineStage[R]): RawPipelineStage[R] =
    if (right eq EmptyPipelineStage) this
    else new RawPipelineStage[R] {
      def apply(ctx: R, cpl: CPL, epl: EPL) = {
        var cplProxy: CPL = Pipeline.Uninitialized
        var eplProxy: EPL = Pipeline.Uninitialized
        val cplProxyPoint: CPL = cplProxy(_)
        val eplProxyPoint: EPL = eplProxy(_)
        val leftPL = left(ctx, cplProxyPoint, epl)
        val rightPL = right(ctx, cpl, eplProxyPoint)
        cplProxy = rightPL.commandPipeline
        eplProxy = leftPL.eventPipeline
        Pipelines(
          commandPL = (if (leftPL.commandPipeline eq cplProxyPoint) rightPL else leftPL).commandPipeline,
          eventPL = (if (rightPL.eventPipeline eq eplProxyPoint) leftPL else rightPL).eventPipeline)
      }
    }
}

trait OptionalPipelineStage[-C <: PipelineContext] extends RawPipelineStage[C] {
  def apply(context: C, commandPL: CPL, eventPL: EPL): Pipelines =
    if (enabled(context)) applyIfEnabled(context, commandPL, eventPL)
    else Pipelines(commandPL, eventPL)

  def enabled(context: C): Boolean

  def applyIfEnabled(context: C, commandPL: CPL, eventPL: EPL): Pipelines
}

object EmptyPipelineStage extends PipelineStage {

  def apply(ctx: PipelineContext, cpl: CPL, epl: EPL) = Pipelines(cpl, epl)

  override def >>[R <: PipelineContext](right: RawPipelineStage[R]): RawPipelineStage[R] = right
}
