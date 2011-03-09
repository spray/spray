package cc.spray.examples.simple

import cc.spray._
import http._
import MimeTypes._

trait Service extends ServiceBuilder {
  
  def restService: Route =
    path("test" / "hello") {
      path("\\d".r) { number =>
        produces(`text/plain`) {
          get { _.respond("The number is: " + number) }
        }
      }
    }
  
}