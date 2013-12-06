Predefined Directives (by trait)
================================

All predefined directives are organized into traits that form one part of the overarching ``Directives`` trait, which
is defined like this:

.. includecode:: /../spray-routing/src/main/scala/spray/routing/Directives.scala
   :snippet: source-quote


.. _Request Directives:

Directives filtering or extracting from the request
---------------------------------------------------

:ref:`MethodDirectives`
  Filter and extract based on the request method.

:ref:`HeaderDirectives`
  Filter and extract based on request headers.

:ref:`PathDirectives`
  Filter and extract from the request URI path.

:ref:`HostDirectives`
  Filter and extract based on the target host.

:ref:`ParameterDirectives`, :ref:`FormFieldDirectives`, :ref:`AnyParamDirectives`
  Filter and extract based on query parameters, form fields, or both.

:ref:`EncodingDirectives`
  Filter and decode compressed request content.

:ref:`MarshallingDirectives`
  Extract the request entity.

:ref:`SchemeDirectives`
  Filter and extract based on the request scheme.

:ref:`SecurityDirectives`
  Handle authentication data from the request.

:ref:`CookieDirectives`
  Filter and extract cookies.

:ref:`BasicDirectives` and :ref:`MiscDirectives`
  Directives handling request properties.


.. _Response Directives:

Directives creating or transforming the response
------------------------------------------------

:ref:`ChunkingDirectives`
  Automatically break a response into chunks.

:ref:`CookieDirectives`
  Set, modify, or delete cookies.

:ref:`EncodingDirectives`
  Compress responses.

:ref:`FileAndResourceDirectives`
  Deliver responses from files and resources.

:ref:`RespondWithDirectives`
  Change response properties.

:ref:`RouteDirectives`
  Complete or reject a request with a response.

:ref:`BasicDirectives` and :ref:`MiscDirectives`
  Directives handling or transforming response properties.


List of predefined directives by trait
--------------------------------------

.. toctree::
   :maxdepth: 1

   any-param-directives/index
   basic-directives/index
   caching-directives/index
   chunking-directives/index
   cookie-directives/index
   debugging-directives/index
   encoding-directives/index
   execution-directives/index
   file-and-resource-directives/index
   form-field-directives/index
   future-directives/index
   header-directives/index
   host-directives/index
   marshalling-directives/index
   method-directives/index
   misc-directives/index
   parameter-directives/index
   path-directives/index
   respond-with-directives/index
   route-directives/index
   scheme-directives/index
   security-directives/index
