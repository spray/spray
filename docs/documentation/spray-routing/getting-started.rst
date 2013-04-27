Getting Started
===============

In order to get you started quickly with a *spray*-based project of your own we maintain two project templates on
github that you can clone and use as a basis. They live in two branches of the spray-template_ github project:

- Clone the on_spray-can_ branch, if you'd like to run *spray-routing* on :ref:`spray-can`
- Clone the on_jetty_ branch, if you'd like to run *spray-routing* on :ref:`spray-servlet`

.. _spray-template: https://github.com/spray/spray-template/
.. _on_spray-can: https://github.com/spray/spray-template/tree/on_spray-can_1.1
.. _on_jetty: https://github.com/spray/spray-template/tree/on_jetty_1.1


.. _SimpleRoutingApp:

SimpleRoutingApp
----------------

*spray-routing* also comes with the ``SimpleRoutingApp`` trait, which you can use as a basis for your first
*spray* endeavours. It reduces the boilerplate to a minimum and allows you to focus entirely on your route structure.

Just use this minimal example application as a starting point:

.. includecode:: code/docs/HttpServiceExamplesSpec.scala
  :snippet: minimal-example

The ``SimpleRoutingApp`` trait extends the *spray-can* :ref:`SprayCanHttpServerApp`, so you have access to its
``system`` member for starting your own actors before starting the HTTP server (if you need to).

This very concise way of bootstrapping a *spray-routing* application works nicely as long as you don't have any special
requirements as to the actor, which is running your route structure. Once you need more control over it, e.g. because
you want to be able to use it as the receiver (or sender) of custom messages, you'll have to "fall back" to the
:ref:`SprayCanHttpServerApp` and create your service actor "manually". The :ref:`Complete Examples` demonstrate, how to
do that.
