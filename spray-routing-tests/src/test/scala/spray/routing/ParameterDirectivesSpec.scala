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

package spray.routing

import shapeless.HNil

class ParameterDirectivesSpec extends RoutingSpec {

  "when used with 'as[Int]' the parameter directive" should {
    "extract a parameter value as Int (using the general `parameters` directive)" in {
      Get("/?amount=123") ~> {
        parameters('amount.as[Int] :: HNil) { echoComplete }
      } ~> check { entityAs[String] === "123" }
    }
    "extract a parameter values as Int (using the `parameter` directive)" in {
      Get("/?amount=123") ~> {
        parameter('amount.as[Int]) { echoComplete }
      } ~> check { entityAs[String] === "123" }
    }
    "cause a MalformedQueryParamRejection on illegal Int values" in {
      Get("/?amount=1x3") ~> {
        parameter('amount.as[Int]) { echoComplete }
      } ~> check {
        rejection must beLike {
          case MalformedQueryParamRejection("amount", "'1x3' is not a valid 32-bit integer value", Some(_)) ⇒ ok
        }
      }
    }
    "supply typed default values" in {
      Get() ~> {
        parameter('amount ? 45) { echoComplete }
      } ~> check { entityAs[String] === "45" }
    }
    "create typed optional parameters that" in {
      "extract Some(value) when present" in {
        Get("/?amount=12") ~> {
          parameter("amount".as[Int]?) { echoComplete }
        } ~> check { entityAs[String] === "Some(12)" }
      }
      "extract None when not present" in {
        Get() ~> {
          parameter("amount".as[Int]?) { echoComplete }
        } ~> check { entityAs[String] === "None" }
      }
      "cause a MalformedQueryParamRejection on illegal Int values" in {
        Get("/?amount=x") ~> {
          parameter("amount".as[Int]?) { echoComplete }
        } ~> check {
          rejection must beLike {
            case MalformedQueryParamRejection("amount", "'x' is not a valid 32-bit integer value", Some(_)) ⇒ ok
          }
        }
      }
    }
  }

  "when used with 'as(HexInt)' the parameter directive" should {
    import spray.httpx.unmarshalling.FromStringDeserializers.HexInt
    "extract parameter values as Int" in {
      Get("/?amount=1f") ~> {
        parameter('amount.as(HexInt)) { echoComplete }
      } ~> check { entityAs[String] === "31" }
    }
    "cause a MalformedQueryParamRejection on illegal Int values" in {
      Get("/?amount=1x3") ~> {
        parameter('amount.as(HexInt)) { echoComplete }
      } ~> check {
        rejection must beLike {
          case MalformedQueryParamRejection("amount",
            "'1x3' is not a valid 32-bit hexadecimal integer value", Some(_)) ⇒ ok
        }
      }
    }
    "supply typed default values" in {
      Get() ~> {
        parameter('amount.as(HexInt) ? 45) { echoComplete }
      } ~> check { entityAs[String] === "45" }
    }
    "create typed optional parameters that" in {
      "extract Some(value) when present" in {
        Get("/?amount=A") ~> {
          parameter("amount".as(HexInt)?) { echoComplete }
        } ~> check { entityAs[String] === "Some(10)" }
      }
      "extract None when not present" in {
        Get() ~> {
          parameter("amount".as(HexInt)?) { echoComplete }
        } ~> check { entityAs[String] === "None" }
      }
      "cause a MalformedQueryParamRejection on illegal Int values" in {
        Get("/?amount=x") ~> {
          parameter("amount".as(HexInt)?) { echoComplete }
        } ~> check {
          rejection must beLike {
            case MalformedQueryParamRejection("amount",
              "'x' is not a valid 32-bit hexadecimal integer value", Some(_)) ⇒ ok
          }
        }
      }
    }
  }

  "The 'parameters' extraction directive" should {
    "extract the value of given parameters" in {
      Get("/?name=Parsons&FirstName=Ellen") ~> {
        parameters("name", 'FirstName) { (name, firstName) ⇒
          complete(firstName + name)
        }
      } ~> check { entityAs[String] === "EllenParsons" }
    }
    "correctly extract an optional parameter" in {
      Get("/?foo=bar") ~> parameters('foo ?) { echoComplete } ~> check { entityAs[String] === "Some(bar)" }
      Get("/?foo=bar") ~> parameters('baz ?) { echoComplete } ~> check { entityAs[String] === "None" }
    }
    "ignore additional parameters" in {
      Get("/?name=Parsons&FirstName=Ellen&age=29") ~> {
        parameters("name", 'FirstName) { (name, firstName) ⇒
          complete(firstName + name)
        }
      } ~> check { entityAs[String] === "EllenParsons" }
    }
    "reject the request with a MissingQueryParamRejection if a required parameters is missing" in {
      Get("/?name=Parsons&sex=female") ~> {
        parameters('name, 'FirstName, 'age) { (name, firstName, age) ⇒
          completeOk
        }
      } ~> check { rejection === MissingQueryParamRejection("FirstName") }
    }
    "supply the default value if an optional parameter is missing" in {
      Get("/?name=Parsons&FirstName=Ellen") ~> {
        parameters("name"?, 'FirstName, 'age ? "29", 'eyes?) { (name, firstName, age, eyes) ⇒
          complete(firstName + name + age + eyes)
        }
      } ~> check { entityAs[String] === "EllenSome(Parsons)29None" }
    }
    "supply the default value if an optional parameter is missing (with the general `parameters` directive)" in {
      Get("/?name=Parsons&FirstName=Ellen") ~> {
        parameters(("name"?) :: 'FirstName :: ('age ? "29") :: ('eyes?) :: HNil) { (name, firstName, age, eyes) ⇒
          complete(firstName + name + age + eyes)
        }
      } ~> check { entityAs[String] === "EllenSome(Parsons)29None" }
    }
  }

  "The 'parameter' requirement directive" should {
    "block requests that do not contain the required parameter" in {
      Get("/person?age=19") ~> {
        parameter('nose ! "large") { completeOk }
      } ~> check { handled must beFalse }
    }
    "block requests that contain the required parameter but with an unmatching value" in {
      Get("/person?age=19&nose=small") ~> {
        parameter('nose ! "large") { completeOk }
      } ~> check { handled must beFalse }
    }
    "let requests pass that contain the required parameter with its required value" in {
      Get("/person?nose=large&eyes=blue") ~> {
        parameter('nose ! "large") { completeOk }
      } ~> check { response === Ok }
    }
    "be useable for method tunneling" in {
      val route = {
        (post | parameter('method ! "post")) { complete("POST") } ~
          get { complete("GET") }
      }
      Get("/?method=post") ~> route ~> check { entityAs[String] === "POST" }
      Post() ~> route ~> check { entityAs[String] === "POST" }
      Get() ~> route ~> check { entityAs[String] === "GET" }
    }
  }

}