package cc.spray.examples.simple

import cc.spray._
import http._
import MimeTypes._

trait Service extends ServiceBuilder {
  
  def restService: Route = {
    path("test" / "echo") {
      produces(`text/plain`) {
        path("\\d+".r) { number =>
          get { _.respond("The number is: " + number) }
        } ~
        path("[A-Z]".r ~ "[a-z]".r) { (upcase, downcase) =>
          get { _.respond("The letters are: " + upcase + " and " + downcase) }
        }
      }
    } ~
    path("resources") {
      getFromResourceDirectory("samples")
    }
  }
}