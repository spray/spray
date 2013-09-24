.. _FutureDirectives:

FuturesDirectives
=================

Future directives can be used to run inner routes once the provided ``Future[T]`` is completed.

.. toctree::
   :maxdepth: 1

   oncomplete
   onsuccess
   onfailure

All future directives are evaluated once at route creation time and its result will always be used
when evaluating the inner routes.

Example
-------

.. includecode:: ../code/docs/directives/FutureDirectivesExamplesSpec.scala
   :snippet: single-execution

In case you need to evaluate the future with every request and the outer routes don't do any extractions (by
extracting a value, a directive make its inner route dynamic) you can wrap your route with the :ref:`-dynamic-`
directive as shown bellow.

.. includecode:: ../code/docs/directives/FutureDirectivesExamplesSpec.scala
   :snippet: per-request-execution
