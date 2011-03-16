package cc.spray
package builders

import org.specs.Specification
import http._
import HttpStatusCodes._
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
    "return a NotFound error with a proper error message if a required parameter is missing" in {
      test(HttpRequest(uri = "/person?name=Parsons&sex=female")) {
        path("person") {
          parameters('name, 'FirstName, 'age) { (name, firstName, age) =>
            get { _ => fail("Should not run") }
          }
        }
      }.response mustEqual failure(NotFound, "Query parameter(s) required: FirstName, age")
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