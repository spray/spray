.. _spray-http:

spray-http
==========

The *spray-http* module contains a fully immutable, case-class based model of the major HTTP data structures, like
HTTP requests, responses and common headers. It also includes a parser for the latter, which is able to construct
the more structured header models from raw unstructured header name/value pairs.


Dependencies
------------

*spray-http* is stand-alone in that it has no dependency on any other *spray* module and not even Akka.
You can therefore use it directly and independently for any other type of HTTP work you might be doing.

Apart from the Scala library (see :ref:`Current Versions` chapter) *spray-http* only depends on parboiled_,
a lightweight PEG parsing library providing the basis for the header parser. Since parboiled_ is also written and
maintained by the members of the *spray* team it's not an "outside" dependency that we have no control over.

.. _parboiled: http://parboiled.org


Installation
------------

The :ref:`maven-repo` chapter contains all the info about how to pull *spray-http* into your classpath.

Afterwards just ``import spray.http._`` to bring all relevant identifiers into scope.


Overview
--------

Since *spray-http* provides the central HTTP data structures for *spray* you will find the following import
in quite a few places around the *spray* code base (and probably your own code as well)::

    import spray.http._

This brings in scope all of the relevant things that are defined here_ and that you'll want to work with, mainly:

- ``HttpRequest`` and ``HttpResponse``, the central message models
- ``ChunkedRequestStart``, ``ChunkedResponseStart``, ``MessageChunk`` and ``ChunkedMessageEnd`` modeling the different
  message parts of request/response streams
- ``HttpHeaders``, an object containing all the defined HTTP header models
- Supporting types like ``Uri``, ``HttpMethods``, ``MediaTypes``, ``StatusCodes``, etc.

A common pattern is that the model of a certain entity is represented by an immutable type (class or trait), while the
actual instances of the entity defined by the HTTP spec live in an accompanying object carrying the name of the type
plus a trailing 's'.

For example:

- The defined ``HttpMethod`` instances live in the ``HttpMethods`` object.
- The defined ``HttpCharset`` instances live in the ``HttpCharsets`` object.
- The defined ``HttpEncoding`` instances live in the ``HttpEncodings`` object.
- The defined ``HttpProtocol`` instances live in the ``HttpProtocols`` object.
- The defined ``MediaType`` instances live in the ``MediaTypes`` object.
- The defined ``StatusCode`` instances live in the ``StatusCodes`` object.

You get the point.

In order to develop a better understanding for how *spray* models HTTP you probably should take some time to browse
around the `spray-http sources`_ (ideally with an IDE that supports proper code navigation).

.. _here: `spray-http sources`_
.. _spray-http sources: https://github.com/spray/spray/tree/release/1.1/spray-http/src/main/scala/spray/http


Content-Type Header
-------------------

One thing worth highlighting is the special treatment of the HTTP ``Content-Type`` header. Since the binary content of
HTTP message entities can only be properly interpreted when the corresponding content-type is known *spray-http* puts
the content-type value very close to the entity data. The ``HttpBody`` type (the non-empty variant of the
``HttpEntity``) is essentially little more than a tuple of the ``ContentType`` and the entity's bytes.
All logic in *spray* that needs to access the content-type of an HTTP message always works with the ``ContentType``
value in the ``HttpEntity``. Potentially existing instances of the ``Content-Type`` header in the ``HttpMessage``'s
header list are ignored!


Custom Media-Types
------------------

*spray-http* defines the most important media types from the `IANA MIME media type registry`_ in the MediaTypes_
object, which also acts as a registry that you can register your own ``CustomMediaType`` instances with:

.. includecode:: code/docs/CustomHttpExtensionExamplesSpec.scala
   :snippet: custom-media-type

Once registered the custom type will be properly resolved, e.g. for incoming requests by :ref:`spray-routing` or
incoming responses by :ref:`spray-client`. File extension resolution (as used for example by the
:ref:`FileAndResourceDirectives`) will work as expected.

.. _IANA MIME media type registry: http://www.iana.org/assignments/media-types/index.html
.. _MediaTypes: https://github.com/spray/spray/blob/master/spray-http/src/main/scala/spray/http/MediaType.scala