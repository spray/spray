package cc.spray.http

sealed trait RangeUnit {
  def value: String
  override def toString = value
}

object RangeUnits {

  class StandardRangeUnit private[RangeUnits] (val value: String) extends RangeUnit
  
  case class CustomRangeUnit(value: String) extends RangeUnit
  
  val bytes = new StandardRangeUnit("bytes")
  
}

