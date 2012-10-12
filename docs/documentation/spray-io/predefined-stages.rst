Predefined Stages
=================

*spray-io* comes with a number of predefined pipeline stages, which you can "bake into" you own pipeline stack, where
you seem fit.

.. _TickGenerator:

TickGenerator
-------------

The TickGenerator__ forms a simple ``event-only stage`` that generates ``Tick`` events in regular intervals.
This is its implementation:

 __ https://github.com/spray/spray/blob/master/spray-io/src/main/scala/spray/io/TickGenerator.scala

.. includecode:: /../spray-io/src/main/scala/spray/io/TickGenerator.scala
   :snippet: source-quote

The ``TickGenerator`` provides the triggers for all timeout checking stages in *spray-io* and :ref:`spray-can`, but, of
course, you can also use it for other purposes.


.. _ConnectionTimeouts:

ConnectionTimeouts
------------------

The ConnectionTimeouts__ pipeline stage provides support for the automatic closing of idle connection after a
configurable time period. The stage is modeled as a ``full stage`` that listens for outgoing ``Send`` commands
as well as incoming ``Received`` events and updates a ``lastActivity`` timestamp, whenever it sees one such message.

It requires a TickGenerator_ stage further down in the stack and uses its ``Tick`` messages as a trigger for checking,
whether the connection has been idle for longer than the allowed time frame. If so, a ``Close`` command is issued.

__ https://github.com/spray/spray/blob/master/spray-io/src/main/scala/spray/io/ConnectionTimeouts.scala


SslTlsSupport
-------------

The SslTlsSupport__ pipeline stage provides for transparent encryption of outgoing ``Send`` commands as well as
decryption of incoming ``Received`` commands. Just add it as a lower-level stage to your pipeline stack, whenever you
need SSL/TLS encryption, and all your network communication will be SSL encrypted automatically.

The ``SslTlsSupport`` also allows for the enabling/disabling of the encryption stage on a per-connection basis.
The ``SslTlsSupport.apply`` methods takes an ``sslEnabled`` parameter of type ``PipelineContext => Boolean``.
The function you supply for this parameter is called whenever a new pipeline instance is constructed (i.e. after the
establishment of a new connection) and its result determines whether to enable the encryption or not.

The :ref:`spray-can` :ref:`HttpClient`, for example, uses this facility to allow for the enabling/disabling of SSL
encryption via the ``tag`` member of the ``Connect`` command. The argument it passes for the ``sslEnabled`` parameter
is this: ``_.handle.tag == HttpClient.SslEnabled``.

The ``SslTlsSupport`` stage requires also requires an ``engineProvider`` parameter, which is a function
``PipelineContext => SSLEngine``. The easiest way to specify an argument for this parameter is to use the default
``ServerSSLEngineProvider`` or ``ClientSSLEngineProvider``, depending on whether you are using the encryption stage
on the client- or the server-side, e.g.::

    val engineProvider = ServerSSLEngineProvider.default

In order to make this line compile you also need to bring into scope either an implicit ``javax.net.ssl.SSLContext``
or an implicit ``SSLContextProvider``.

__ https://github.com/spray/spray/blob/master/spray-io/src/main/scala/spray/io/SslTlsSupport.scala