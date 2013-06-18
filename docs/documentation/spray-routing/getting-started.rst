Getting Started
===============

Check out the :ref:`Introduction / Getting Started <Getting Started>` chapter for information about the template
project you can use to quickly bootstrap your own *spray-routing* application.


.. _SimpleRoutingApp:

SimpleRoutingApp
----------------

*spray-routing* also comes with the ``SimpleRoutingApp`` trait, which you can use as a basis for your first
*spray* endeavours. It reduces the boilerplate to a minimum and allows you to focus entirely on your route structure.

Just use this minimal example application as a starting point:

.. includecode:: code/docs/HttpServiceExamplesSpec.scala
   :snippet: minimal-example

This very concise way of bootstrapping a *spray-routing* application works nicely as long as you don't have any special
requirements with regard to the actor which is running your route structure. Once you need more control over it, e.g.
because you want to be able to use it as the receiver (or sender) of custom messages, you'll have to "fall back" to
creating your service actor "manually". The :ref:`Complete Examples` demonstrate how to do that.
