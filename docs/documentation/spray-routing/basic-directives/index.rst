.. _BasicDirectives:

BasicDirectives
===============

Basic directives are building blocks for building :ref:`Custom Directives`. As such they
usually aren't used in a route directly but rather in the definition of new directives.

.. _ProvideDirectives:

Directives to provide values to inner routes
--------------------------------------------

These directives allow to provide the inner routes with extractions. They can be distinguished
on two axes: a) provide a constant value or extract a value from the ``RequestContext`` b) provide
a single value or an HList of values.

  * :ref:`-extract-`
  * :ref:`-hextract-`
  * :ref:`-provide-`
  * :ref:`-hprovide-`

.. _Request Transforming Directives:

Directives transforming the request
-----------------------------------

  * :ref:`-mapRequestContext-`
  * :ref:`-mapRequest-`

.. _Response Transforming Directives:

Directives transforming the response
------------------------------------

These directives allow to hook into the response path and transform the complete response or
the parts of a response or the list of rejections:

  * :ref:`-mapHttpResponse-`
  * :ref:`-mapHttpResponseEntity-`
  * :ref:`-mapHttpResponseHeaders-`
  * :ref:`-mapHttpResponsePart-`
  * :ref:`-mapRejections-`


.. _Responder Chain Directives:

Directives hooking into the responder chain
-------------------------------------------

These directives allow to hook into :ref:`The Responder Chain`. The first two allow transforming the response message to
a new message. The latter one allows to completely replace the response message with the execution of a new route.

  * :ref:`-mapRouteResponse-`
  * :ref:`-mapRouteResponsePF-`
  * :ref:`-routeRouteResponse-`


Directives changing the execution of the inner route
----------------------------------------------------

  * :ref:`-mapInnerRoute-`

Directives alphabetically
-------------------------

.. toctree::
   :maxdepth: 1

   extract
   hextract
   hprovide
   mapHttpResponse
   mapHttpResponseEntity
   mapHttpResponseHeaders
   mapHttpResponsePart
   mapInnerRoute
   mapRejections
   mapRequest
   mapRequestContext
   mapRouteResponse
   mapRouteResponsePF
   noop
   pass
   provide
   routeRouteResponse
