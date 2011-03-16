package cc.spray.examples.simple

import cc.spray._
import http._
import MediaTypes._

trait Service extends ServiceBuilder {
  
  def sampleService = service {
    path("test" / "echo") {
      path("\\d+".r) { number =>
        get { _.complete("The number is: " + number) }
      } ~
      path("[A-Z]".r ~ "[a-z]".r) { (upcase, downcase) =>
        get { _.complete("The letters are: " + upcase + " and " + downcase) }
      }
    } ~
    path("resources") {
      getFromResourceDirectory("samples")
    }
  }
}