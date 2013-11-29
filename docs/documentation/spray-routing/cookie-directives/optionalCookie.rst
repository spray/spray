.. _-optionalCookie-:

optionalCookie
==============

Extracts an optional cookie with a given name from a request.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/CookieDirectives.scala
   :snippet: optionalCookie

Description
-----------

Use the :ref:`-cookie-` directive instead if the inner route does not handle a missing cookie.


Example
-------

.. includecode:: ../code/docs/directives/CookieDirectivesExamplesSpec.scala
   :snippet: optionalCookie
