.. _-delete-:

delete
======

Matches requests with HTTP method ``DELETE``.

Description
-----------

This directive filters the incoming request by its HTTP method. Only requests with
method ``DELETE`` are passed on to the inner route. All others are rejected with a
``MethodRejection``, which is translated into a ``405 Method Not Allowed`` response
by the default :ref:`RejectionHandler`.


Example
-------

.. includecode:: ../code/docs/directives/MethodDirectivesExampleSpec.scala
  :snippet: delete-method
