package cc.spray
package examples.client

import json.{JsonFormat, DefaultJsonProtocol}

case class Elevation(location: Location, elevation: Double)
case class Location(lat: Double, long: Double)
case class GoogleApiResult[T](status: String, results: List[T])

trait ElevationJsonProtocol extends DefaultJsonProtocol {
  implicit val locationFormat = jsonFormat(Location, "lat", "lng")
  implicit val elevationFormat = jsonFormat(Elevation, "location", "elevation")
  implicit def GoogleApiResultFormat[T :JsonFormat] = jsonFormat(GoogleApiResult.apply[T], "status", "results")
}