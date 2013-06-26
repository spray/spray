.. _RequestLevelApi:

Request-level API
=================

The request-level API is the most convenient way of using *spray-can*'s client-side. It internally builds upon the
:ref:`HostLevelApi` to provide you with a simple and easy-to-use way of retrieving HTTP responses from remote servers.

Just send an ``HttpRequest`` instance to the ``Http`` extensions like this:

.. includecode:: ../code/docs/HttpClientExamplesSpec.scala
   :snippet: request-level-example

The request you send to ``IO(Http)`` must have an absolute URI or contain a ``Host`` header. *spray-can* will forward
it to the host connector (see :ref:`HostLevelApi`) for the target host (and start it up if it is not yet running).

If you want to specify config settings for either the host connector or the underlying connections that differ from
what you have configured in your ``application.conf`` you can either "prime" a host connector by sending an explicit
``Http.HostConnectorSetup`` command before issuing the first request to this host or send a tuple
``(Request, Http.HostConnectorSetup)`` combining the request with the ``Http.HostConnectorSetup`` command. The latter
also allows the request to have a relative URI and no host header since the target host is already specified with the
connector setup command.

All other aspects of the request-level API are identical to the host-level counterpart.