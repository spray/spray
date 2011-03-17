package cc.spray
package builders

import org.specs.Specification
import http._
import test.SprayTest

class ParameterBuildersSpec extends Specification with SprayTest with ServiceBuilder {

  "The 'parameter' directive" should {
    "extract the value of given required parameters" in {
      test(HttpRequest(uri = "/person?name=Parsons&FirstName=Ellen")) {
        path("person") {
          parameters("name", 'FirstName) { (name, firstName) =>
            get { _.complete(firstName + name) }
          }
        }
      }.response.content.as[String] mustEqual Right("EllenParsons")
    }
    "ignore additional parameters" in {
      test(HttpRequest(uri = "/person?name=Parsons&FirstName=Ellen&age=29")) {
        path("person") {
          parameters("name", 'FirstName) { (name, firstName) =>
            get { _.complete(firstName + name) }
          }
        }
      }.response.content.as[String] mustEqual Right("EllenParsons")
    }
    "reject the request with QueryParamRequiredRejection if required parameters are missing" in {
      test(HttpRequest(uri = "/person?name=Parsons&sex=female")) {
        path("person") {
          parameters('name, 'FirstName, 'age) { (name, firstName, age) =>
            get { _ => fail("Should not run") }
          }
        }
      }.rejections mustEqual
              Set(PathMatchedRejection, QueryParamRequiredRejection("FirstName"), QueryParamRequiredRejection("age"))
    }
    "supply the default value if an optional parameter is missing" in {
      test(HttpRequest(uri = "/person?name=Parsons&FirstName=Ellen")) {
        path("person") {
          parameters("name"?, 'FirstName, 'age ? "29") { (name, firstName, age) =>
            get { _.complete(firstName + name + age) }
          }
        }
      }.response.content.as[String] mustEqual Right("EllenParsons29")
    }
  }

}