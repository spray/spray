.. _spray-routing:

spray-routing
=============

The *spray-routing* module provides a high-level, very flexible routing DSL for elegantly defining RESTful web services.
Normally you would use it either on top of a :ref:`spray-can` :ref:`HttpServer` or inside of a servlet container
together with :ref:`spray-servlet`.

.. toctree::
   :maxdepth: 1

   dependencies
   installation
   configuration
   getting-started
   key-concepts/index
   advanced-topics/index
   predefined-directives-alphabetically
   predefined-directives-by-trait
   examples


Minimal Example
---------------

This is a complete, very basic *spray-routing* application:

.. includecode:: code/docs/HttpServiceExamplesSpec.scala
   :snippet: minimal-example

It starts a *spray-can* :ref:`HttpServer` on localhost and replies to GET requests to ``/hello`` with a simple response.


.. _Longer Example:

Longer Example
--------------

The following is a *spray-routing* route definition that tries to show off a few features. The resulting service does
not really do anything useful but its definition should give you a feel for what an actual API definition with
*spray-routing* will look like:

.. includecode:: code/docs/HttpServiceExamplesSpec.scala
   :snippet: longer-example





