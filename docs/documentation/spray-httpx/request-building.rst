.. _RequestBuilding:

Request Building
================

When you work with *spray* you'll occasionally want to construct HTTP requests, e.g. when talking to an HTTP server
with :ref:`spray-client` or when writing tests for your server-side API with :ref:`spray-testkit`.

For making request construction more convenient *spray-httpx* provides the RequestBuilding__ trait, that defines a
simple DSL for assembling HTTP requests in a concise and readable manner.

Take a look at these examples:

.. includecode:: code/docs/RequestBuildingExamplesSpec.scala
   :snippet: example-1


__ https://github.com/spray/spray/blob/master/spray-httpx/src/main/scala/spray/httpx/RequestBuilding.scala