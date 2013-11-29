.. _-cookie-:

cookie
======

Extracts a cookie with a given name from a request or otherwise rejects the request with a ``MissingCookieRejection`` if
the cookie is missing.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/CookieDirectives.scala
   :snippet: cookie

Description
-----------

Use the :ref:`-optionalCookie-` directive instead if you want to support missing cookies in your inner route.


Example
-------

.. includecode:: ../code/docs/directives/CookieDirectivesExamplesSpec.scala
   :snippet: cookie
