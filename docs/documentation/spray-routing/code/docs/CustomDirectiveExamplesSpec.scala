package docs

import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import spray.routing.HttpService


class CustomDirectiveExamplesSpec extends Specification with Specs2RouteTest {

  "example-1" in {
    import shapeless._
    import spray.routing._
    import Directives._

    val twoIntParameters: Directive[Int :: Int :: HNil] =
      parameters('a.as[Int], 'b.as[Int])

    val myDirective: Directive[String :: HNil] =
      twoIntParameters.map {
        case a :: b :: HNil => (a + b).toString
      }

    // test `myDirective` using the testkit DSL
    Get("/?a=2&b=5") ~> myDirective(complete(_)) ~> check {
      entityAs[String] === "7"
    }
  }

  "example-2" in {
    import shapeless._
    import spray.routing._
    import Directives._

    val intParameter: Directive[Int :: HNil] = parameter('a.as[Int])

    val myDirective: Directive[Int :: HNil] =
      intParameter.flatMap {
        case a :: HNil if a > 0 => provide(2 * a)
        case _ => reject()
      }

    // test `myDirective` using the testkit DSL
    Get("/?a=21") ~> myDirective(i => complete(i.toString)) ~> check {
      entityAs[String] === "42"
    }
    Get("/?a=-18") ~> myDirective(i => complete(i.toString)) ~> check {
      handled must beFalse
    }
  }

}
