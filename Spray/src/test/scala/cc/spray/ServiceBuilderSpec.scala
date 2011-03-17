package cc.spray

import org.specs.Specification
import http._
import HttpMethods._
import test.SprayTest

class ServiceBuilderSpec extends Specification with SprayTest with ServiceBuilder {

  "routes created by the concatenation operator '~'" should {
    "yield the first sub route if it succeeded" in {
      test(HttpRequest(GET)) {
        get { _.complete("first") } ~ get { _.complete("second") }
      }.response.content.as[String] mustEqual Right("first")    
    }
    "yield the second sub route if the first did not succeed" in {
      test(HttpRequest(GET)) {
        post { _.complete("first") } ~ get { _.complete("second") }
      }.response.content.as[String] mustEqual Right("second")    
    }
    "collect rejections from both sub routes" in {
      test(HttpRequest(DELETE)) {
        get { { _ => fail("Should not run") } } ~ put { { _ => fail("Should not run") } }
      }.rejections mustEqual Set(MethodRejection(GET), MethodRejection(PUT))
    }
  }
  
}