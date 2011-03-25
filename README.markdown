# Spray

Spray is a lightweight Scala framework for building RESTful web services on top of Akka actors and Akka Mist.
It sports the following main features:

* Completely asynchronous, non-blocking, actor-based request handling
* Powerful, flexible and extensible internal Scala DSL for declaratively defining your web service behavior
* Immutable model of the HTTP protocol, decoupled from the underlying servlet container
* Full testability of your REST services, without the need to fire up containers or actors
* Basic content negotiation 
  
## Example

    import cc.spray._

    trait HelloServiceBuilder extends ServiceBuilder {
      
      val helloService = {
        path("hello") {
          get { _.complete(<h1>Say hello to Spray</h1>) } ~
          put { _.complete("received PUT request for resource '/hello'") }
        }
      }
      
    }


## Quick start

1. Git-clone the template project. Alternatively, download and extract a [tarball](http://github.com/sirthias/spray-template/tarball/master) or [zip](http://github.com/sirthias/spray-template/zipball/master).

        $ git clone git://github.com/sirthias/spray-template.git my-project

2. Change directory into your clone:

        $ cd my-project

3. Launch [SBT](http://code.google.com/p/simple-build-tool) (note that you will need SBT 0.7.5.RC0 or 0.7.5.RC1 !):

        $ sbt

4. Fetch the dependencies:

        > update

5. Start Jetty, enabling continuous compilation and reloading:

        > jetty-run
        > ~prepare-webapp

6. Browse to `http://localhost:8080/`

7. Start hacking on `src/main/scala/com/example/HelloServiceBuilder.scala`


## The Big Picture

Spray builds on Akka Mist, which provides a very thin layer between the servlet container and Akka actors with the goal
of handling incoming HTTP requests as quickly as possible in an asynchronous manner.
  
When a request comes in from the underlying webserver Akkas Mist layer suspends it and passes it on to Sprays
[RootService][] actor. The [RootService][] actor creates a [RequestContext][] for request and sends it off to all
attached [HttpService][] actors. Each [HttpService][] actor then feeds the [RequestContext][] into its "Route", which
completes (resumes) the request at the earliest possible time.
  

## Routes

Routes are the central elements of the web services built with Spray. In Spray a Route is a simple function 
`RequestContext => Unit`. Contrary to what you might initially expect a Route does not return anything.
Rather, all response processing (i.e. everything that needs to be done _after_ your code has handled a request) is
performed in "continuation-style" via the 'responder' function of the [RequestContext][]. Typically the innermost Route
of your Route structure calls `requestContext.complete(...)`, which invokes the contexts responder function, which then
runs all response postprocessing logic including the final request resumption as a chain of nested function calls.
This design has the advantage of being completely non-blocking as well as actor-friendly since, this way, it's possible
to simply send off the RequestContext to another actor in a "fire-and-forget" manner, without having to worry about
results handling.


## Constructing Routes

As already stated a Route is a function `RequestContext => Unit`, so the simplest route is

    ctx => ctx.complete("Response")
    
or shorter:

    _.complete("Response")
    
which simply completes all requests with a static response.
In order to treat requests differently depending on their HTTP method, URI, entity content, etc. we need two basic
route combinators:

* Route filters, which only let requests satisfying a given filter condition pass and reject all others
* Route chaining, which tries a second route if a given first one was rejected
 
Once we have these two basic elements we can construct arbitrarily complex Routes.
Spray comes with a number of predefined Route filters and makes it easy to create your own. For example, to only answer
GET requests wrap your Route with the `get` directive: 

    get { _.complete("Received GET request") }

The `get` directive will let GET requests pass to its inner route but reject all others. (See chapter "rejections"
below for what exactly "rejects" means in this context).

Routes can be chained with the '~' operator, so the following snippet
 
    get { _.complete("Received GET request") } ~
    put { _.complete("Received PUT request") }

defines a route that will handle PUT requests differently from GET requests.  
To limit your response to certain URI paths simply wrap your route with one (or more) `path` directives:
  
    path("names" / "bob") {
      get { _.complete("Received GET request") } ~
      put { _.complete("Received PUT request") }  
    }

The path directive comes with its own small mini-DSL for path specification and is quite powerful (see below for more).
Sometimes you would like to filter based on condition A _or_ condition B. So rather than having to say   

    get { myActor ! _ } ~
    put { myActor ! _ }
    
you can also say

    (get | put) { myActor ! _ }
     
This works for any number of same-type route filters.


## Predefined Route Filters
 
All Route filters take an inner Route or an expression resulting in a Route as parameter and provide to the outside
another Route that only responds to requests matching the filter criterion. All non-matching requests are rejected.

### Method Filters

The most basic Route filters are the method filters. There is one Route filter per HTTP method, so in total there are
7 (`delete`, `get`, `head`, `options`, `post`, `put`, `trace`). They only let requests with the respective HTTP method
pass.
Additionally there is also the `method` directive, which takes an [HttpMethod][] constant as parameter:

    import cc.spray.http.HttpMethods._
    
    method(POST) {
      ...
    }


### Path Filters

The route returned by a path filter only responds to requests whose so-far-unmatched path matches the filter expression.
Filter expressions are specified in a mini-DSL, which is probably best explained by example.
This route here only responds to requests to paths beginning with "hello":

    path("hello") { ... }

The following three routes all respond to path beginning with "hello/bob":    

    path("hello/bob") {
      ...
    }
    
    path("hello" / "bob") { ... }
    
    path("hello") {
      path("bob") {
        ...
      }
    }

Apart from plain strings the filter expression can also contain regular expressions, which extract the matched strings
from the path and make them available to the inner route as shown in these examples:

    path("order" / "\\d+".r) { id =>
      _.complete("The order id is: " + id) // 'id' contains the string of matched digits    
    }
    
    path("employee" / "[^/]+".r) { employee =>
      path("salary" / "\\d\\d\\d\\d".r / "\\d\\d".r) { (year, month) =>
        ...          
      } ~
      path("tenure") {
        ...
      }
    }

If a regular expression does not contain any capturing groups (as in the examples above) they extract the full match.
However, a regex filter element can also contain one capturing group. In this case only the string matched by the
capturing group is extracted:

    path("order(\\d+)".r) { id =>
      ... // 'id' contains the string of matched digits    
    }

A filter regex is not allowed to contain more than one capturing group. To circumvent this restriction you can also
chain filter expression element with the '~' operator, which (as opposed to the '/' operator) does not introduce a
separating slash character but simply connects two elements directly:

    path("post-" ~ "\\d\\d\\d\\d".r ~ "-(\\d\\d)".r ~ "-(\\d\\d)".r) { (year, month, day) =>
      ... // matches for example "/post-2011-03-23"          
    }    


### Host Filters

The `host` directive filter on the hostname part of the requests URI. There are three variants:

* `host(String)` simply filters on an exact match of the hostname
* `host(String => Boolean)` lets the given predicate function decide whether to reject the request
* `host(Regex)` performs a match against the given regex and extracts the matched string
 
For example:

    host("www.spray.cc") {
      ... // only responds to requests on host "www.spray.cc")
    }
    
    host("(www1|www2).spray.cc".r) { server =>
      ...    
    }
 
If the regex contains a capturing group the extraction is limited to the string matched by the group. The regex is not
allowed to contain more than one capturing group.


### Parameter Filters

The `parameter(s)` directive filters on the existence of query parameters and extracts their (string) values:
 
    parameter("age") { age =>
      _.complete("The 'age' parameter has value: " + age)    
    }
    
    parameters("name"?, 'firstname, 'sex ? "male") { (name, firstname, sex) =>
      ...    
    }

You can use symbols or strings for specifying the parameter name. The route returned by the `parameter(s)` directive
will reject the requests if a parameter with the given name is not present. This can be prevented by specifying a
default value with the "?" operator. You can also leave off the argument to the "?" operator in which case the empty
string will be used. Therefore `'name?` acts as an optional parameter, which will extract the empty string if not
present.

There is one more feature to the `parameter` directive: You can use the "!" operator to filter on a _required_ parameter
value. This can, for example, be useful for method tunneling:

    (put | parameter('method ! "put")) {
      ... // responds to PUT requests or requests where query parameter "method" has value "put" 
    }


### Marshalling Filters

There are three more predefined Route filters called `contentAs`, `produces` and `handledBy`, which are discussed in 
the section about Marshalling further below.


## Non-Filtering Route Directives

Apart from Route directives with filter (and potentially extactor) semantics Spray provides a number of non-filtering
Route directives.

### Cached

The `cached` directive wraps its inner route with a cache for GET requests. Consider this example:

    path("some" / "Resource") {
      cached {
        get {
          ... // expensive logic for retrieving the resource representation
        } ~
        put {
          ...          
        }
      }
    }

In this example the "expensive logic" will only run once. After that all subsequent get requests to the resource will
be served from the internal cache of the cached directive (each cache directive keeps it's own cache as a simple
WeakHashMap). To non-GET requests the `cached` directive is completely transparent.  

Apart from its inner Route the `cached` directive takes another, implicit argument: A cache keyer function of signature
`RequestContext => CacheKey`, it returns a [CacheKey][]. This is the default cache keyer implementation:
 
    implicit def defaultCacheKeyer(ctx: RequestContext): CacheKey = {
      if (ctx.request.method == HttpMethods.GET) CacheOn(ctx.request.uri) else DontCache
    }

Applications can influence the exact caching behavior by providing their own implicit "cache keyer" function.


### Detached

Normally all your routing code runs within the context of its HttpService actor, which is fine, as long as your code
does not contain blocking calls (e.g. to I/O) or really expensive operations.
There are two ways of moving request handling off the HttpService actor to another actor: using explicit message
sending or the `detached` directive.


#### Moving Request Handling to a custom Actor 

You can ship off request handling to another actor at any time like this:

    get { myActor ! _ }
    
Your actor should be prepared to receive a [RequestContext][] object, which it can handle in any way it wants, e.g. with
the help another, "personal" route.


#### The `detached` Directive

The `detached` directive simplifies the common case of transfering the handling of the current request to another,
_newly spawned_ actor. For example in the following scenario:

    path("some" / "resource") {
      get {
        detached {
          ... // expensive logic for retrieving the resource representation
        }
      } ~
      put {
        ...          
      }
    }    

the "expensive logic" will not run within the context of the main routes HttpService actor but its own private actor.
Note that Sprays "continuation-style" route combinators have an interesting side effect, that might not be obvious on
first sight. Consider this slightly changed example:
 
    path("some" / "resource") {
      detached {
        get {
          ... // expensive logic for retrieving the resource representation
        }
      } ~
      put {
        ... // PUT logic          
      }
    } 

At first sight this seems identical to the example above, however, there is one important difference. This time, during
Route processing, the `detached` directive is encountered first, before the `get` filter is run. Therefore the request
handling is split off to a fresh actor for _all_ requests to the "some/Resource" path, even non-GET requests. Even
though the Route description doesn't make it obvious also the PUT logic is run in the newly spawned actor.
This example is equivalent to the following, more readable one (which should therefore be preferred):

    path("some" / "resource") {
      detached {
        get {
          ... // expensive logic for retrieving the resource representation
        } ~
        put {
          ... // PUT logic          
        }
      } 
    }

If you need some more control over what actors the `detached` directive dispatches to you can supply your own implicit
implementation of a `Route => Actor` function.

        
### File and Resource Directives
 
Many web services also need to serve a small number of static files, e.g. for documentation purposes.
Spray makes this easy with the `getFromFile`, `getFromResource`, `getFromDirectory` and `getFromResourceDirectory`
directives. `getFromFile` and `getFromResource` respond to GET requests with the contents of a given file or (JAR)
resource, e.g.:
  
    path("api" / "documentation") {
      getFromFile("/var/www/fancy-service/doc.html")
    }

The -directory variants serve up whole directories. The following route for example
 
    path("api" / "documentation") {
      getFromDirectory("/var/www/fancy-service/documentation/")
    } 

will respond to GET requests to `/api/documentation/chapter/1.html` with the contents of the file
`/var/www/fancy-service/documentation/chapter/1.html`.

In order to allow for some more URI flexibility you can also supply a "path rewriter" function `String => String`,
which allows for custom preprocessing of the relevant path segment, before it is passed on to the file system.
You might for example use it to append ".html" extensions for files that don't have one.

The four `getFrom...` directives automatically set the content type of HTTP response to the media type matching the
file extension. For example ".html" files will be served with "Content-Type: text/html" while ".txt" files will receive
a "Content-Type: text/plain". You can also very easily supply your own media types and/or file extensions. See
the "More Examples" section for further leads on this.


### Transformer Directives

Finally Spray provides the three "transformer" directives: `requestTransformedBy`, `responseTransformedBy` and
`routingResultTransformedBy`. They allow you to inject custom logic at any point in your Route structure, for example
to preprocess a request before it hits some other Route or postprocess the generated response.
The "markdown-server" example (see the "More Examples" section below) uses `responseTransformedBy` to transform
responses with the custom content type `text/x-markdown` to `text/html`. 


## Marshalling

HTTP requests and responses can have an "entity body", which can be text or binary content. However, when consuming
requests and producing responses in your application you normally want to work with your domain objects rather than
low level representations in XML, JSON or other serialization formats.

Spray supports decoupling your application logic from these serialization converns by separating out marshalling
(converting domain objects to a serialized format) and unmarshalling (creating domain objects from a serialized format).

Suppose for example your application provides actions upon Person resources, which are represented in your code like
this:

    case class Person(name: string, firstname: String, age: int)
    
You can say something like this:

    path("person" / "\\d+".r) { id =>
      get {
        val person: Person = ... // load the Person from the database using the id
        _.complete(person) // uses the in-scope Marshaller to convert the Person into a format accepted by the client
      } ~
      put {
        contentAs[Person] { person =>
          ... // operate directly on the Person object, e.g. write it to the database                          
        }
      }
    }

When you use the [RequestContexts][RequestContext] `complete` method with an argument of a custom type the Scala
compiler will look for an in-scope implicit Marshaller object for your type to do the job of converting your custom
object to a representation accepted by the client. Marshallers are really easy to write, take a look at the
[StringMarshaller][] and [NodeSeqMarshaller][] implementations that Spray already comes with. 

As an alternative to using the [RequestContexts][RequestContext] `complete` method with an argument of a custom type
you can also use the `produces` directive, as shown in this example:

    path("person" / "\\d+".r) { id =>
      get {
        produces[Person] { personCompleter =>
          val person: Person = ... // load the Person from the database using the id
          personCompleter(person)
        }
      }
    }
    
The `produces` directive in this example "extracts" a function `Person => Unit` that you can use to complete the request
with. This decouples the Marshaller resolution from the code that actually completes the request. For example, you could
ship the "completer" function off to another actor, which does not need to know anything about Marshallers.

The opposite of marshalling is unmarshalling, i.e. the creation of custom objects from XML, JSON and the like. This job
is done by Unmarshallers, which just like Marshallers are really easy to write for your custom types and need to be
available as implicit objects. The `contentAs` directive extracts the requests content, converts it using the in-scope
Unmarshallers and makes it available to your inner routes.

As an added convenience Spray also comes with the `handledBy` directive, which combines `contentAs` and `produces`.
This is its implementation:

    def handledBy[A :Unmarshaller, B: Marshaller](f: A => B): Route = {
      contentAs[A] { a =>
        produces[B] { produce =>
          _ => produce(f(a))
        }
      }
    }

## Error handling

All directives detailed above produce proper error messages according to the requirements of the HTTP spec, if something
goes wrong. For example if the request cannot be handledbecause no path filter matched Spray will produce a
`404 NotFound` error. If the path matched but no route for the requests HTTP method is defined Spray will produce a
`405 MethodNotAllowed` error. If the marshallers or unmarshallers in your Routes cannot deserialize the request content
or the client does not accept any of the serialization options Spray will return `415 UnsupportedMediaType` or
`406 NotAcceptable` errors respectively.

The way in which this is done (and the way in which this can be influenced) is discussed in the next section. 

## Rejections

## Testing

## More Examples

## FAQ

Multiple services

## Path Policy

## Links

* <http://sirthias.github.com/spray/api/> for the spray API scaladoc

[RootService]:
[RequestContext]:
[HttpService]:
[HttpMethod]:
[CacheKey]:
[StringMarshaller]:
[NodeSeqMarshaller]: