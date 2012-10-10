.. _Predefined Directives:

Predefined Directives (alphabetically)
======================================

.. rst-class:: table table-striped

====================================== =================================================================================
Directive                              Description
====================================== =================================================================================
:ref:`-alwaysCache-`                   Wraps its inner Route with caching support using a given cache instance, ignores
                                       request ``Cache-Control`` headers
:ref:`-authenticate-`                  Tries to authenticate the user with a given authenticator and either extract a
                                       an object representing the user context or rejects
:ref:`-authorize-`                     Applies a given authorization check to the request and rejects if it doesn't pass
:ref:`-autoChunk-`                     Converts non-rejected responses from its inner Route to chunked responses using a
                                       given chunk size
:ref:`-cache-`                         Wraps its inner Route with caching support using a given cache instance
:ref:`-cachingProhibited-`             Rejects the request if it doesn't contain a ``Cache-Control`` header with
                                       ``no-cache`` or ``max-age=0``
:ref:`-cancelAllRejections-`           Adds a ``TransformationRejection`` to rejections from its inner Route, which
                                       cancels other rejections according to a predicate function
:ref:`-clientIP-`                      Extracts the IP address of the client from either the ``X-Forwarded-For``,
                                       ``Remote-Address`` or ``X-Real-IP`` request header
:ref:`-complete-`                      Completes the request with a given response, several overloads
:ref:`-cookie-`                        Extracts an ``HttpCookie`` with a given name or rejects if no such cookie is
                                       present in the request
:ref:`-decodeRequest-`                 Decompresses incoming requests using a given Decoder
:ref:`-delete-`                        Rejects all non-DELETE requests
:ref:`-deleteCookie-`                  Adds a ``Set-Cookie`` header expiring the given cookie to all ``HttpResponse``
                                       replies of its inner Route
:ref:`-detachTo-`                      Executes its inner Route in the context of the actor returned by a given function
:ref:`-dynamic-`                       Rebuilds its inner Route for every request anew
:ref:`-encodeResponse-`                Compresses responses coming back from its inner Route using a given Decoder
:ref:`-entity-`                        Unmarshalls the requests entity according to a given definition, rejects in
                                       case of problems
:ref:`-extract-`                       Extracts a single value from the ``RequestContext`` using a function
                                       ``RequestContext => T``
:ref:`-failWith-`                      Bubbles the given error up the response chain, where it is dealt with by the
                                       closest :ref:`-handleExceptions-` directive and its ExceptionHandler
:ref:`-filter-`                        Extracts zero or more values or rejects depending on the outcome of a filter
                                       function
:ref:`-flatMapRouteResponse-`          Transforms all responses coming back from its inner Route with a
                                       ``Any => Seq[Any]`` function
:ref:`-flatMapRouteResponsePF-`        Same as :ref:`-flatMapRouteResponse-`, but with a ``PartialFunction``
:ref:`-formField-`                     Extracts the value of an HTTP form field, rejects if the request doesn't come
                                       with a field matching the definition
:ref:`-formFields-`                    Same as :ref:`-formField-`, except for several fields at once
:ref:`-get-`                           Rejects all non-GET requests
:ref:`-getFromDirectory-`              Completes GET requests with the content of a file underneath a given directory
:ref:`-getFromFile-`                   Completes GET requests with the content of a given file
:ref:`-getFromFileName-`               Completes GET requests with the content of the file with a given name
:ref:`-getFromResource-`               Completes GET requests with the content of a given resource
:ref:`-getFromResourceDirectory-`      Same as :ref:`-getFromDirectory-` except that the file is not fetched from the
                                       file system but rather from a "resource directory"
:ref:`-handleExceptions-`              Converts exceptions thrown during evaluation of its inner Route into
                                       ``HttpResponse`` replies using a given ExceptionHandler
:ref:`-handleRejections-`              Converts rejections produced by its inner Route into ``HttpResponse`` replies
                                       using a given RejectionHandler
:ref:`-handleWith-`                    Completes the request using a given function. Uses the in-scope ``Unmarshaller``
                                       and ``Marshaller`` for converting to and from the function
:ref:`-headerValue-`                   Extracts an HTTP header value using a given function, rejects if no value can
                                       be extracted
:ref:`-headerValuePF-`                 Same as :ref:`-headerValue-`, but with a ``PartialFunction``
:ref:`-hextract-`                      Extracts an ``HList`` of values from the ``RequestContext`` using a function
:ref:`-host-`                          Rejects all requests with a hostname different from a given definition,
                                       can extract the hostname using a regex pattern
:ref:`-hostName-`                      Extracts the hostname part of the requests ``Host`` header value
:ref:`-hprovide-`                      Injects an ``HList`` of values into a directive, which provides them as
                                       extractions
:ref:`-jsonpWithParameter-`            Wraps its inner Route with JSONP support
:ref:`-logHttpResponse-`               Produces a log entry for every ``HttpResponse`` coming back from its inner Route
:ref:`-logRequest-`                    Produces a log entry for every incoming request
:ref:`-logRequestResponse-`            Produces a log entry for every request and corresponding response or rejection
                                       coming back from its inner Route
:ref:`-logRouteResponse-`              Produces a log entry for every response or rejection coming back from its inner
                                       route
:ref:`-mapHttpResponse-`               Transforms the ``HttpResponse`` coming back from its inner Route
:ref:`-mapHttpResponseEntity-`         Transforms the entity of the ``HttpResponse`` coming back from its inner Route
:ref:`-mapHttpResponseHeaders-`        Transforms the headers of the ``HttpResponse`` coming back from its inner Route
:ref:`-mapInnerRoute-`                 Transforms its inner Route with a ``Route => Route`` function
:ref:`-mapRejections-`                 Transforms all rejections coming back from its inner Route
:ref:`-mapRequest-`                    Transforms the incoming ``HttpRequest``
:ref:`-mapRequestContext-`             Transforms the ``RequestContext``
:ref:`-mapResponder-`                  Transforms the current responder ``ActorRef``
:ref:`-mapRouteResponse-`              Transforms all responses coming back from its inner Route with a ``Any => Any``
                                       function
:ref:`-mapRouteResponsePF-`            Same as :ref:`-mapRouteResponse-`, but with a ``PartialFunction``
:ref:`-method-`                        Rejects if the request method does not match a given one
:ref:`-noop-`                          Does nothing, i.e. passes the ``RequestContext`` unchanged to its inner Route
:ref:`-optionalCookie-`                Extracts an ``HttpCookie`` with a given name, if the cookie is not present in the
                                       request extracts ``None``
:ref:`-parameter-`                     Extracts the value of a request query parameter, rejects if the request doesn't
                                       come with a parameter matching the definition
:ref:`-parameterMap-`                  Extracts the requests query parameters as a Map[String, String]
:ref:`-parameters-`                    Same as :ref:`-parameter-`, except for several parameters at once
:ref:`-patch-`                         Rejects all non-PATCH requests
:ref:`-path-`                          Extracts zero+ values from the ``unmatchedPath`` of the ``RequestContext``
                                       according to a given ``PathMatcher``, rejects if no match
:ref:`-pathPrefix-`                    Same as :ref:`-path-`, but also matches (and consumes) prefixes of the unmatched
                                       path
:ref:`-post-`                          Rejects all non-POST requests
:ref:`-produce-`                       Uses the in-scope marshaller to extract a function that can be used for
                                       completing the request with an instance of a custom type
:ref:`-provide-`                       Injects a single value into a directive, which provides it as an extraction
:ref:`-put-`                           Rejects all non-PUT requests
:ref:`-redirect-`                      Completes the request with redirection response of the given type to a given URI
:ref:`-reject-`                        Rejects the request with a given set of rejections
:ref:`-rejectEmptyRequests-`           Rejects empty requests
:ref:`-rejectEmptyResponse-`           Converts responses with an empty entity into a rejection
:ref:`-requestEncodedWith-`            Rejects the request if its encoding doesn't match a given one
:ref:`-requestEntityEmpty-`            Rejects the request if its entity is not empty
:ref:`-requestEntityPresent-`          Rejects the request if its entity is empty
:ref:`-respondWithHeader-`             Adds a given response header to all ``HttpResponse`` replies from its inner
                                       Route
:ref:`-respondWithHeaders-`            Same as :ref:`-respondWithHeader-`, but for several headers at once
:ref:`-respondWithLastModifiedHeader-` Adds a ``Last-Modified`` header to all ``HttpResponse`` replies from its inner
                                       Route
:ref:`-respondWithMediaType-`          Overrides the media-type of all ``HttpResponse`` replies from its inner Route,
                                       rejects if the media-type is not accepted by the client
:ref:`-respondWithSingletonHeader-`    Adds a given response header to all ``HttpResponse`` replies from its inner
                                       Route, if a header with the same name is not yet present
:ref:`-respondWithSingletonHeaders-`   Same as :ref:`-respondWithSingletonHeader-`, but for several headers at once
:ref:`-respondWithStatus-`             Overrides the response status of all ``HttpResponse`` replies coming back from
                                       its inner Route
:ref:`-responseEncodingAccepted-`      Rejects the request if the client doesn't accept a given encoding for the
                                       response
:ref:`-rewriteUnmatchedPath-`          Transforms the ``unmatchedPath`` of the ``RequestContext`` using a given function
:ref:`-setCookie-`                     Adds a ``Set-Cookie`` header to all ``HttpResponse`` replies of its inner Route
:ref:`-validate-`                      Passes or rejects the request depending on evaluation of a given conditional
                                       expression
====================================== =================================================================================
