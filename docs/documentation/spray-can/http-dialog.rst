.. _HttpDialog:

HttpDialog
==========

As a thin layer on top of the rather low-level :ref:`HttpClient` *spray-can* provides a convenience mini-DSL that makes
working with HTTP connections a bit easier. An ``HttpDialog`` encapsulates an exchange of HTTP messages over the course
of *one single connection* and provides a fluent API for constructing a "chain" of scheduled tasks, which define what to
do over the course of the message exchange with the server.

It is probably best explained by example.

The following snippet shows a minimal, single-request ``HttpDialog``:

.. includecode:: code/docs/HttpDialogExamplesSpec.scala
   :snippet: example-1

The dialog opens a connection to a given host/port, fires one request and returns a future on the response. The
connection is automatically closed after the response has come in.

An ``HttpDialog`` can also be used to fire several requests across a connection. Here is a non-pipelined two-request
dialog, this time using the ``RequestBuilding`` helper from :ref:`spray-httpx`:

.. includecode:: code/docs/HttpDialogExamplesSpec.scala
   :snippet: example-2

Two requests are fired across the connection, with the second only going out after the response to the first one
has come in. The result is now a future on several responses.

Let's look at a pipelined three-request dialog:

.. includecode:: code/docs/HttpDialogExamplesSpec.scala
   :snippet: example-3

This snippet fires three requests in a row across the connection, without waiting for responses in between. The result
is again a future on several responses.

You can create "request -> response -> request" dialogs like this:

.. includecode:: code/docs/HttpDialogExamplesSpec.scala
   :snippet: example-4

Here the second request is built using the response to the first. The result is a future on a single response, namely
the last one.

As you can see an ``HttpDialog`` can be convenient and light-weight tool to quickly fire a few requests. However, it
only works with one single HTTP connection and doesn't support a higher-level features (like redirection following).
If you need something more powerful check out the :ref:`spray-client` module.