.. _Connection Tags:

Connection Tags
===============

Every connection (handle) has a custom ``tag: Any`` member that it initially receives from the command that triggered
the connection, which is either the ``Bind`` for server-side or ``Connect`` for client-side connections.

Additionally the connection tag can be changed right before a new handle is registered by overriding the
``connectionTag`` method (from the ``ConnectionActors`` trait), which is especially useful for servers that would like
to attach individual tags to accepted connections and not use (the same) ``Bind`` tag for all of them.

Currently there are three uses for connection tags:

1. Enable/disable encryption on a per-connection basis by having the tag object implement the ``SslTlsSupport.Enabling``
   trait:

   .. includecode:: /../spray-io/src/main/scala/spray/io/SslTlsSupport.scala
      :snippet: Enabling-trait

   If the tag does not implement this trait the default setting applies, which is specified as an argument to the
   ``SslTlsSupport`` pipeline stage creator. (For example, the *spray-can* :ref:`HttpClient` has a default of
   "not encrypted" and the *spray-can* :ref:`HttpServer` a default of "encrypted", when the
   ``spray.can.server.ssl-encryption`` config setting is enabled and ``SslTlsSupport`` available.)

2. Enable log marking: If the tag object implements the new ``spray.io.LogMarking`` trait all log messages produced by
   the *spray-io* and *spray-can* layers for this connection will be prefixed with the respective log marker string.
   This allows for easy grepping of all log messages related to a specific connection across all layers, even in massive
   logs. The respective log logic is also accessible for your custom layers on top of *spray-io* / *spray-can* via the
   ``spray.io.TaggableLog`` facility.

3. Custom uses: Since your code has access to the tag from everywhere, including custom pipeline stages, tags can be
   used as a channel for any kind of custom data needs.