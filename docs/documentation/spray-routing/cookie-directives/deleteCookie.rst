.. _-deleteCookie-:

deleteCookie
============

Adds a header to the response to request the removal of the cookie with the given name on the client.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/CookieDirectives.scala
   :snippet: deleteCookie

Description
-----------

Use the :ref:`-setCookie-` directive to update a cookie.

Example
-------

.. includecode:: ../code/docs/directives/CookieDirectivesExamplesSpec.scala
   :snippet: deleteCookie
