.. _-scheme-:

scheme
======

Rejects a request if its Uri scheme does not match a given one.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/SchemeDirectives.scala
   :snippet: scheme

Description
-----------

The ``scheme`` directive can be used to match requests by their Uri scheme, only passing
through requests that match the specified scheme and rejecting all others.

A typical use case for the ``scheme`` directive would be to reject requests coming in over
http instead of https, or to redirect such requests to the matching https URI with a
``MovedPermanently``.

For simply extracting the scheme name, see the :ref:`-schemeName-` directive.

Example
-------

.. includecode:: ../code/docs/directives/SchemeDirectivesExamplesSpec.scala
   :snippet: example-2

