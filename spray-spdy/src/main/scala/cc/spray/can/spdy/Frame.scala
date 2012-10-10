package cc.spray.can.spdy

import cc.spray.can.parsing.ParsingState

sealed trait Frame extends ParsingState

sealed trait ControlFrame extends Frame

case class SynStream(streamId: Int, associatedTo: Int, priority: Int, fin: Boolean, unidirectional: Boolean, keyValues: Map[String, String]) extends ControlFrame
case class SynReply(streamId: Int, fin: Boolean, keyValues: Map[String, String]) extends ControlFrame
case class RstStream(streamId: Int, statusCode: Int) extends ControlFrame
case class Ping(pingId: Int, rawData: Array[Byte]) extends ControlFrame

case class Setting(id: Int, flags: Int, value: Int)
case class Settings(clearPersistedSettings: Boolean, settings: Seq[Setting]) extends ControlFrame

case class DataFrame(streamId: Int, flags: Int, length: Int, data: Array[Byte]) extends Frame
case class FrameParsingError(errorCode: Int) extends Frame
