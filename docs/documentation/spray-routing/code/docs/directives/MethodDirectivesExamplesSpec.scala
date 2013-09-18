package docs.directives

import spray.routing.Directives

class MethodDirectivesExamplesSpec extends DirectivesSpec {
  "delete-method" in {
    val route = Directives.delete { complete("This is a DELETE request.") }

    Delete("/") ~> route ~> check { 
      entityAs[String] === "This is a DELETE request." 
    }
  }

  "get-method" in {
    val route = get { complete("This is a GET request.") }

    Get("/") ~> route ~> check { 
      entityAs[String] === "This is a GET request." 
    }
  }
    
  "head-method" in {
    val route = head { complete("This is a HEAD request.") }

    Head("/") ~> route ~> check { 
      entityAs[String] === "This is a HEAD request." 
    }
  }
   
  "options-method" in {
    val route = options { complete("This is an OPTIONS request.") }

    Options("/") ~> route ~> check { 
      entityAs[String] === "This is an OPTIONS request."
    }
  }

  "patch-method" in {
    val route = patch { complete("This is a PATCH request.") }
    
    Patch("/", "patch content") ~> route ~> check { 
      entityAs[String] === "This is a PATCH request." 
    }
  }
    
  "post-method" in {
    val route = post { complete("This is a POST request.") }

    Post("/", "post content") ~> route ~> check { 
      entityAs[String] === "This is a POST request."
    }
  }

  "put-method" in {
    val route = put { complete("This is a PUT request.") }
    
    Put("/", "put content") ~> route ~> check { 
      entityAs[String] === "This is a PUT request." 
    }
  }
}
