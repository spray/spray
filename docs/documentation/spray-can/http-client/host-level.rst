.. _HostLevelApi:

Host-level API
==============

As opposed to the :ref:`ConnectionLevelApi` the host-level API relieves you from manually opening and closing each
individual HTTP connection. It autonomously manages a configurable pool of connections to *one particular server*.


Starting an HttpHostConnector
-----------------------------

The core of this API is the ``HttpHostConnector`` actor, whose class, as with all other *spray-can* actors, you don't
get in direct contact with from your application. All communication happens purely via actor messages, the majority of
which are defined in the `spray.can.Http`_ object.

You ask *spray-can* to start a new ``HttpHostConnector`` for a given host by sending an ``Http.HostConnectorSetup``
message to the ``Http`` extension as such::

    IO(Http) ! Http.HostConnectorSetup("www.spray.io", port = 80)

Apart from the host name and port the ``Http.HostConnectorSetup`` message also allows you to specify socket options and
a larger number of configuration settings for the connector and the connections it is to manage.

If there is no connector actor running for the given combination of hostname, port and settings *spray-can* will start
a new one, otherwise the existing one is going to be re-used.
The connector will then respond with an ``Http.HostConnectorInfo`` event message, which repeats the connectors
``ActorRef`` and setup command (for easy matching against the result of an "ask").

.. _spray.can.Http: https://github.com/spray/spray/blob/master/spray-can/src/main/scala/spray/can/Http.scala#L29


Using an HttpHostConnector
--------------------------

Once you've got a hold of the connectors ``ActorRef`` you can send it one or more *spray-http* ``HttpRequestPart``
messages. The connector will send the request across one of the connections it manages according to the following logic:

- if `HTTP pipelining`_ is not enabled (the default) the request is

  - dispatched to the first idle connection in the pool if there is one
  - dispatched to a newly opened connection if there is no idle one and less than the configured ``max-connections``
    have been opened so far
  - queued and sent across the first connection that becomes available (i.e. either idle or unconnected) if all
    available connections are currently busy with open requests

- if `HTTP pipelining`_ is enabled the request is dispatched to

  - the first idle connection in the pool if there is one
  - a newly opened connection if there is no idle one and less than the configured ``max-connections``
    have been opened so far
  - the connection with the least open requests if all connections already have requests open

As soon as a response for a request has been received it is dispatched as a ``HttpResponsePart``
instance to the sender of the respective request. If the server indicated that it doesn't want to reuse the connection
for other requests (either via a ``Connection: close`` header on an ``HTTP/1.1`` response or a missing
``Connection: Keep-Alive`` header on an ``HTTP/1.0`` response) the connector actor closes the connection after receipt
of the response thereby freeing up the "slot" for a new connection.

.. _HTTP pipelining: http://en.wikipedia.org/wiki/HTTP_pipelining


Retrying a Request
------------------

If the ``max-retries`` connector config setting is greater than zero the connector retries idempotent requests for which
a response could not be successfully retrieved. Idempotent requests are those whose HTTP method is defined to be
idempotent by the HTTP spec, which are all the ones currently modelled by *spray-http* except for the ``PATCH`` and
``POST`` methods.

When a response could not be received for a certain request there are essentially three possible error scenarios:

1. The request got lost on the way to the server.
2. The server experiences a problem while processing the request.
3. The response from the server got lost on the way back.

Since the host connector cannot know which one of these possible reasons caused the problem and therefore ``PATCH`` and
``POST`` requests could have already triggered a non-idempotent action on the server these requests cannot be retried.

In these cases, as well as when all retries have not yielded a proper response, the connector dispatches a
``Status.Failure`` message with a ``RuntimeException`` holding a respective error message to the sender of the request.


Connector Shutdown
------------------

The connector config contains an ``idle-timeout`` setting which specifies the time period after which an idle connector,
i.e. one without any open connections, will automatically shut itself down. Since, by default, the connections in the
connectors connection pool also have an idle-timeout active an unused connector will eventually be cleaned up completely
if left unused.

However, in order to speed up the shutdown a host connector can be sent an ``Http.CloseAll`` command, which
triggers an explicit closing of all connections. After all connections have been properly closed the connector will
dispatch an ``Http.ClosedAll`` event message to all senders of ``Http.CloseAll`` messages before stopping itself.

A subsequent sending of an identical ``Http.HostConnectorSetup`` command to the ``Http`` extension will then trigger the
creation of a fresh connector instance.