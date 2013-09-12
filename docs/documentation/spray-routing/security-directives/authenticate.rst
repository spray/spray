.. _-authenticate-:

authenticate
============

Authenticates a request by checking credentials supplied in the request and extracts a value
representing the authenticated principal.

Signature
---------

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/SecurityDirectives.scala
   :snippet: authenticate

``AuthMagnet`` instances:[1]_

.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/SecurityDirectives.scala
   :snippet: fromFutureAuth
.. includecode:: /../spray-routing/src/main/scala/spray/routing/directives/SecurityDirectives.scala
   :snippet: fromContextAuthenticator

Type definitions:

.. includecode:: /../spray-routing/src/main/scala/spray/routing/authentication/package.scala
   :snippet: auth-types


.. [1] See `The Magnet Pattern`_ for an explanation of magnet-based overloading.
.. _`The Magnet Pattern`: /blog/2012-12-13-the-magnet-pattern/

Description
-----------

On the lowest level, ``authenticate``, takes either a ``Future[Authentication[T]]`` which
authenticates based on values from the lexical scope or a value of
``type ContextAuthenticator[T] = RequestContext ⇒ Future[Authentication[T]]`` which
extracts authentication data from the ``RequestContext``. The returned value of type ``Authentication[T]`` must
either be the authenticated principal which will be supplied to the inner route or a rejection if the authentication
failed to reject the request with.

Both variants return futures so that the actual authentication procedure runs detached from the route processing
and processing of the inner route will be continued once the authentication finished. This
allows longer-running authentication tasks (like looking up credentials in a db) to run without blocking
the ``HttpService`` actor, freeing it for other requests. The ``authenticate`` directive itself
isn't tied to any HTTP-specific details so that various authentication schemes can be implemented
on top of ``authenticate``.

Standard HTTP-based authentication which uses the ``WWW-Authenticate`` header containing challenge
data and ``Authorization`` header for receiving credentials is implemented in subclasses of ``HttpAuthenticator``.

HTTP Basic Authentication
+++++++++++++++++++++++++

*spray* supports `HTTP basic authentication`_ through the ``BasicHttpAuthenticator`` and provides a series of
convenience constructors for different scenarios with ``BasicAuth()``. Make sure to use basic authentication only over
SSL because credentials are transferred in plaintext.


.. _`HTTP basic authentication`: http://en.wikipedia.org/wiki/Basic_auth


Implementing a ``UserPassAuthenticator``
****************************************

The most generic way of deploying HTTP basic authentication uses a ``UserPassAuthenticator`` to validate a user/password
combination. It is defined like this:

.. includecode:: /../spray-routing/src/main/scala/spray/routing/authentication/package.scala
   :snippet: user-pass-authenticator

Its job is to map a user/password combination (if existent in the request) to an authenticated custom principal of type
``T`` (if authenticated).

.. includecode:: ../code/docs/directives/SecurityDirectivesExamplesSpec.scala
   :snippet: authenticate-custom-user-pass-authenticator


From configuration
******************

There are several overloads to configure users from the configuration file. Obviously, this is neither a secure
(plaintext passwords) nor a scalable approach. If you don't pass in a custom config users are configured from
the :ref:`routing-configuration` path ``spray.routing.users``.

.. includecode:: ../code/docs/directives/SecurityDirectivesExamplesSpec.scala
   :snippet: authenticate-from-config

From LDAP
*********

(todo)
