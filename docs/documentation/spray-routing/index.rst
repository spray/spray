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

This is what a very basic *spray-routing* service definition:

.. includecode:: code/docs/HttpServiceExamplesSpec.scala
 :snippet: minimal-example


.. _Longer Example:

Longer Example
--------------

The following is a *spray-routing* route definition that tries to show off a few features. The resulting service does
not really do anything useful but its definition should give you a feel for what an actual API definition with
*spray-routing* will look like:

.. includecode:: code/docs/HttpServiceExamplesSpec.scala
 :snippet: longer-example





