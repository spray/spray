.. _spray-http:

spray-http
==========

The *spray-http* module contains a fully immutable, case-class based model of the major HTTP data structures, like
HTTP requests, responses and common headers. It also includes a parser for the latter, which is able to construct
the more structured header models from raw, unstructured header name/value pairs.


Dependencies
------------

*spray-http* is stand-alone in that it has no dependency on any other *spray* module and not even Akka.
You can therefore use it directly and independently for any other type of HTTP work you might be doing.

Apart from the Scala library (see :ref:`current-versions` chapter) *spray-http* only depends on parboiled_,
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
- Supporting types like ``HttpMethods``, ``MediaTypes``, ``StatusCodes``, etc.

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
.. _spray-http sources: https://github.com/spray/spray/tree/master/spray-http/src/main/scala/spray/http


2-Stage Message Parsing
-----------------------

The center point of *spray-http* is the ``HttpMessage`` class with its sub-classes ``HttpRequest`` and ``HttpResponse``.
``HttpMessage`` defines the things that are shared between requests and responses, most importantly the HTTP headers
and the message entity.

One thing that's important to understand is that *spray* follows **a two-stage approach** for creating the model
structure upon reception of an HTTP message from the network. In order to not waste resources on parsing and creation
of model objects that the application doesn't require the low-level modules *spray-can* and *spray-servlet* construct
only "basic versions" of an ``HttpRequest`` or ``HttpResponse``.

In their "basic" incarnations an ``HttpMessage`` contains all its headers as ``RawHeader`` instances, which are not
much more than a simple pair of name/value strings. For some applications this might be all that's required.
However, if you call the ``parseHeaders`` method of the message object all headers that *spray-http* has a
higher-level model for (the ones defined in ``HttpHeaders``) are "upgraded" and returned with a fresh copy of the
message object.

The ``HttpRequest`` has a similar way of "upgrading" the URI and the query string from their raw, unparsed counterparts.

The main point, where this "upgrading" of the request headers, uri and query string currently happens, is in the
``runRoute`` method of the ``HttpService`` trait in the :ref:`spray-routing` module. It transforms the "basic"
``HttpRequest`` instances coming in from the :ref:`spray-can` or :ref:`spray-servlet` layer into their fully-parsed
state, which is later used by many of the various :ref:`spray-routing` directives.


Content-Type Header
-------------------

One other thing worth highlighting is the special treatment of the HTTP ``Content-Type`` header. Because of its crucial
role in content negotiation its value has a special status in *spray*. It is part of the ``HttpBody``, which is the
non-empty variant of an ``HttpEntity``. The value of the ``Content-Type`` header is parsed into its higher-level model
class (i.e. "upgraded") even by the low-level :ref:`spray-can` and :ref:`spray-servlet` layers.


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