Migration from M8
=================

Big breaking changes
--------------------

_`Replacement of HttpBody by HttpData`
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

``HttpBody`` was completely replaced by ``HttpData``. ``HttpData`` is a data structure that
abstracts over either ``ByteString`` buffers (``HttpData.Bytes``), data from files
(``HttpData.FileBytes``), and combinations of both (``HttpData.Compound``). This allows spray-can
to combine both heap byte buffers and data from files without ever having to load file data into a
heap buffer.
The companion object of ``HttpEntity`` contains lots of overloaded apply methods to create an
``HttpEntity`` without having to deal with ``HttpData`` directly.

_`Authentication`
~~~~~~~~~~~~~~~~~

Authentication rejections were remodelled. There's now only one rejection type, ``AuthenticationFailedRejection``, that
contains a cause (``CredentialsMissing`` or ``CredentialsRejected``) and a list of headers to add to the response
as a challenge for the client. Previously, challenge generation wasn't handled uniformly and challenges weren't generated
at all in some cases. For your custom authenticator, you now have to generate the challenge headers directly in
the ``ContextAuthenticator`` implementation. ``HttpAuthenticator`` now has a new abstract method to implement to return
challenge headers.

_`Chunking support`
~~~~~~~~~~~~~~~~~~~

With request chunk aggregation turned off (``spray.can.server.request-chunk-aggregation-limit = 0``)
spray-can will deliver request chunks as is to the user-level handler.
Previously, all chunks of a request came in subsequently without any interaction needed from the handler. Now the
``ChunkedRequestStart`` message has to be acknowledged with a ``RegisterChunkHandler`` message which contains the
ActorRef of an actor to handle the chunks before any actual ``MessageChunks`` will be delivered. This allows to
separate the handling of multiple incoming request chunk streams to several chunk handlers.

_`PathMatcher infrastructure`
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The path matching infrastructure was changed by replacing automatic behavior with more explicit directives. Mainly, this
affects uses of nested ``path("")`` directives. Previously, ``path("")`` would either match 1) nothing, 2) a single slash,
or 3) two slashes before the path end. Since the question of whether to deliver identical content under two different
URIs (adding a trailing slash to a URI results in a semantically *different* URI!) is one of continuous discussion
(see for example: http://googlewebmastercentral.blogspot.de/2010/04/to-slash-or-not-to-slash.html) we want to make it
easy for your to pick your strategy and be consistent about it. With the new version, you can use one of the new
directives instead of ``path("")`` to be explicit about what to match:

  * ``pathEnd``, which matches just the path end without any trailing slashes
  * ``pathSingleSlash``, which matches the path only when it ends with a slash
  * ``pathEndOrSingleSlash``, which matches in both cases

Directives ``path`` and ``pathPrefix`` previously matched an optional beginning slash from the (remaining) path. The
beginning slash is now required. If that's not what you want use ``rawPathPrefix`` instead.

Also:

 * ``PathMatcher.Slash`` replaces the old ``PathMatcher.Slash_!`` which matches exactly one slash.
 * ``PathMatcher.PathEnd`` now matches exactly the end of the path (before: an optional slash as well).
 * ``PathMatcher.Segments`` also doesn't match a trailing slash any more.
 * ``PathMatcher.Empty`` was renamed to ``PathMatcher.Neutral``.

It is now easy to create path matchers for optional or repeated matches. Use ``PathMatcher.?`` to make a ``PathMatcher``
optional. Use ``PathMatcher.repeat`` to capture several matches.

_`(Un)marshalling`
~~~~~~~~~~~~~~~~~~

(TODO)

 - Introduction of ToResponseMarshaller etc.
 - RequestContext.complete overloads gone
 - CompletionMagnet gone


Comprehensive list of breaking changes
--------------------------------------

The following list is an annotated version of all commits that were marked as breaking (``!``) since M8. You may
find additional information on how to fix broken code by looking into the linked commits. Changes are often accompanied
with test changes that show how code can be fixed.

spray-cache
~~~~~~~~~~~

 - ExpiringLruCache uses ``Duration`` instead of ``Long`` for timeouts [2394720]_.
 - ``LruCache.apply`` doesn't allow ``Duration.Zero`` any more but ``Duration.Inf``, instead, for not specifying
   any timeout [2394720]_.

.. [2394720] `use util.Timestamp in ExpiringLruCache <http://github.com/spray/spray/commit/2394720>`_

spray-can
~~~~~~~~~

Server-side changes:

 - Http handlers must answer ``ChunkedRequestStart`` message with ``RegisterChunkHandler`` command
   (see `chunking support`_) [d738e54]_
 - ``ServerSettings`` timeout settings were moved into a separate class [9193318]_.


Client-side changes:

 - new member ``HostConnectorSettings.maxRedirects`` to enable automatic redirection handling on the client side
   [98365ff]_
 - ``spray.can.client.user-agent-header`` vs. specifying a custom ``User-Agent`` header behavior changed: a custom
   ``User-Agent`` header now always overrides any configuration setting. The setting is used when there's no custom
   header specified [6fec00c]_.
 - Connection failure and request timeout now produce dedicated exceptions on the client side [4b48875]_.
 - ``HostConnectorSettings.connectionSettings`` are now read from ``spray.can.client`` instead of from
   ``spray.can.host-connector.client`` [9abbcf6]_.
 - ``spray.can.client.ssl-encryption`` is gone. Instead, ``Http.Connect`` got a new ``sslEncryption`` parameter
   replacing the global setting. [e922cd4]_.
 - ``HostConnectorSetup`` must now be created from hostname and port. Before it was created from an ``InetSocketAddress``
   which wasn't enough to distinguish virtual hosts [a47f3b0]_.

Other changes:

 - ``keepOpenOnPeerClosed`` is not supported for Http and was removed from ``Http.Register`` [f6b0292]_.
 - ``ClientConnectionSettings`` and ``ServerSettings`` got a new member ``maxEncryptionChunkSize`` [0b5ef36]_.
 - Lots of formerly public types belonging to spray's private API were marked as such [da29cdf]_.
 - ``spray.can.server.response-size-hint`` was adapted to ``spray.can.server.response-header-size-hint``. The same
   for ``spray.can.client.request-size-hint`` [ba1ae77]_.
 - ``Content-Length.length`` is now a ``Long`` value. Also ``spray.can.server.parsing.max-content-length`` and
   ``incoming-auto-chunking-threshold-size`` [b2fee8d]_.
 - ``SetIdleTimeout`` command now always resets the timeout [ab17f00]_.
 - ``SslTlsSupport`` pipeline stage now publishes a ``SSLSessionEstablished`` event with session details [80982d4]_.
 - New ``ParserSettings.sslSessionInfoHeader`` setting which enables the automatic addition of a synthetic
   ``SSL-Session-Info`` header to a request/response with SSL session information [e486900]_.
 - ``ClientConnectionSettings.userAgentHeader`` is now modelled directly by an ``Option[User-Agent]``. [da12531]_.

.. [ab17f00] `use util.Timestamp instead of longs for timeout checking <http://github.com/spray/spray/commit/ab17f00>`_
.. [f6b0292] `get rid of Http.Register.keepOpenOnPeerClosed, fixes #401 <http://github.com/spray/spray/commit/f6b0292>`_
.. [0b5ef36] `add max-encryption-chunk-size setting to ClientConnectionSettings and ServerSettings <http://github.com/spray/spray/commit/0b5ef36>`_
.. [98365ff] `Implement redirection following (issue #132) <http://github.com/spray/spray/commit/98365ff>`_
.. [d738e54] `require services to respond to ChunkedRequestStart with RegisterChunkHandler, fixes #473 <http://github.com/spray/spray/commit/d738e54>`_
.. [da29cdf] `"privatize" all classes/objects not meant to be part of public API <http://github.com/spray/spray/commit/da29cdf>`_
.. [6fec00c] `only render default User-Agent if no such header was explicit given, fixes #462 <http://github.com/spray/spray/commit/6fec00c>`_
.. [9193318] `break out ServerSettings timeout settings into sub case class, closes #489 <http://github.com/spray/spray/commit/9193318>`_
.. [ba1ae77] `upgrade to new HttpEntity / HttpData model <http://github.com/spray/spray/commit/ba1ae77>`_
.. [b2fee8d] `make Content-Length a long value, fixes #443 <http://github.com/spray/spray/commit/b2fee8d>`_
.. [4b48875] `introduce dedicated exceptions for connection failure and request timeout for host-level API <http://github.com/spray/spray/commit/4b48875>`_
.. [9abbcf6] `when creating HostConnectorSettings expect client settings at spray.can.client, fixes #408 <http://github.com/spray/spray/commit/9abbcf6>`_
.. [e922cd4] `move client.ssl-encryption setting from reference.conf into Http.Connect message, fixes #396 <http://github.com/spray/spray/commit/e922cd4>`_
.. [a47f3b0] `replace InetSocketAddress in HostConnectorSetup with hostname/port pair, fixes #394 <http://github.com/spray/spray/commit/a47f3b0>`_
.. [80982d4] `Publish SSLSessionEstablished event from SslTlsSupport upon successful SSL handshaking <http://github.com/spray/spray/commit/80982d4>`_
.. [e486900] `Add SSLSessionInfo header to requests on server and responses on client <http://github.com/spray/spray/commit/e486900>`_
.. [da12531] `model user-agent-header value as User-Agent to fail fast, fixes #458 <http://github.com/spray/spray/commit/da12531>`_


spray-http
~~~~~~~~~~

 - ``Access-Control-Allow-Origin`` and ``Origin`` header models now have members of newly introduced type ``HttpOrigin``
   instead of the previous ``Uri`` which didn't completely match the model [015f3c6]_.
 - ``Renderer.seqRenderer`` and related signatures changed [e058a43]_.
 - in ``Uri.Query`` a ``'='`` is rendered even for empty values unless the special value ``Query.EmptyValue`` is used.
   Also, a query parsed from ``?key=`` will now be rendered the same way (previously,  a trailing ``'='`` was always stripped) [d2b8bba]_.
 - ``(Multipart)FormData.fields`` are now represented as ``Seq`` to be able to model duplicate fields [ad593d1]_.
 - ``HttpMessage.entityAccepted`` was renamed to ``HttpMessage.isEntityAccepted`` [5d78dae]_.
 - `Replacement of HttpBody by HttpData`_ [c6f49cc]_.
 - Many charsets in ``HttpCharsets`` are not any more available as static values. Use
   ``HttpCharset.getForKey("windows-1252")`` to access a particular charset [f625b5a]_.
 - ``Uri.Query.apply`` and ``Uri.Host.apply`` have a new ``charset`` parameter [88a25f7]_.
 - ``Uri.Query`` has a new subtype ``Uri.Query.Raw`` which will be generated when parsing with mode
   ``Uri.ParsingMode.RelaxedWithRawQuery`` [d8a9ee4]_.
 - ``MediaRanges.custom`` was renamed to ``MediaRange.custom`` [a915b8f]_.
 - ``HttpSuccess`` and ``HttpFailure`` are not public API any more. Use ``StatusCode.isSuccess`` instead [a9e0d2c]_.
 - ``HttpIp`` was replaced by ``RemoteAddress`` which also supports "unknown" addresses. ``X-Forwarded-For.ips`` member
   was renamed to ``addresses``. ``Remote-Address.ip`` member was renamed to `address` [443b0d8]_.

.. [015f3c6] `add HttpOrigin and use it for Access-Control-Allow-Origin and Origin headers, fixes #579 <http://github.com/spray/spray/commit/015f3c6>`_
.. [e058a43] `allow creation of custom MediaTypes with '*' as a subtype when called by the parser, fixes #529 <http://github.com/spray/spray/commit/e058a43>`_
.. [d2b8bba] `introduce a distinction between "?key=" and "?key" in queries, fixes #460 <http://github.com/spray/spray/commit/d2b8bba>`_
.. [ad593d1] `make multipart form-data more flexible but have it adhere to the RFC more strictly <http://github.com/spray/spray/commit/ad593d1>`_
.. [5d78dae] `add CONNECT method and support for custom HTTP methods, closes #428 <http://github.com/spray/spray/commit/5d78dae>`_
.. [c6f49cc] `introduce HttpData model replacing the byte array in HttpBody and MessageChunk, closes #365 <http://github.com/spray/spray/commit/c6f49cc>`_
.. [f625b5a] `add small extensions to Uri model <http://github.com/spray/spray/commit/f625b5a>`_
.. [88a25f7] `make only standard charsets available as constants, fixes #340 <http://github.com/spray/spray/commit/88a25f7>`_
.. [a915b8f] `fix raw queries still performing %-decoding and not being rendered as raw, fixes #330 <http://github.com/spray/spray/commit/a915b8f>`_
.. [d8a9ee4] `add support for Accept-Header extensions and media-type parameters, closes #310 <http://github.com/spray/spray/commit/d8a9ee4>`_
.. [a9e0d2c] `support for custom status codes, fixes #564 <http://github.com/spray/spray/commit/a9e0d2c>`_
.. [443b0d8] `remodel HttpIp to RemoteAddress, fixes #638 <http://github.com/spray/spray/commit/443b0d8>`_


spray-routing
~~~~~~~~~~~~~

 - ``RequestContext.complete`` overloads were removed in favor of using the marshalling infrastructure
   (see `(Un)marshalling`) [4d787dc]_.
 - ``CompletionMagnet`` is gone in favor of the new ``ToResponseMarshaller`` infrastructure [7a36de5]_.
 - ``FieldDefMagnetAux``, ``ParamDefMagnetAux``, and ``AnyParamDefMagnetAux`` are gone and replaced by a simpler
   construct [d86cb80]_.
 - ``RequestContext.marshallingContext`` is gone. ``produce`` directive loses its ``status`` and ``header`` parameter
   which can be replaced by using an appropriate ``ToResponseMarshaller`` [b145ced]_.
 - ``AuthenticationFailedRejection`` now directly contains challenge headers to return. There's no need to implement
   a (fake) ``HttpAuthenticator`` to make use of the rejection (see `Authentication`_) [9c9b976]_.
 - ``FileAndResourceDirectives.withTrailingSlash`` and ``fileSystemPath`` are now private [ab35761]_.
 - ``decompressRequest`` and ``compressResponse`` now always need parentheses. Also, encoding directives like the
   ``compressResponse`` automatically use the ``autoChunkFileBytes`` directives to avoid having to load potentially huge
   files into memory [e3defb4]_.
 - ``(h)require`` directives can now take several rejections instead of an Option of only one [9c11228]_.
 - ``detachTo`` is gone in favor of ``detach()`` which always needs parentheses. The underlying implementation is now
   Future-based and needs an (implicit or explicit) ``ExecutionContext`` or ``ActorRefFactory`` in scope [ead4a70]_.
 - ``PathMatcher.(flat)Map`` were renamed to ``PathMatcher.h(flat)Map``. ``map`` and ``flatMap`` were reintroduced for
   ``PathMatcher1`` instances [8c91851]_.
 - ``AuthenticationFailedRejection`` and ``AuthenticationRequiredRejection`` were merged and
   remodelled. [034779d]_
 - ``PathMatchers.Empty`` was renamed to ``PathMatchers.Neutral`` [ee7fe47]_.
 - ``Slash_!`` is gone and ``Slash`` got its semantics. ``PathEnd`` now just matches the end of the path.
   ``PathDirectives`` were adapted to have the same semantics as before [1480e73]_.
 - ``UserPassAuthenticator.cached`` was renamed to ``CachedUserPassAuthenticator.apply`` [1326046]_.
 - ``PathMatcher.apply`` now takes a ``Path`` prefix instead of a ``String`` [3ff3471]_.
 - ``PathMatcher.Segments`` doesn't match trailing slashes anymore. Implicit infrastructure for ``PathMatcher.?``
   was changed [8ee49d7]_.
 - ``pathEnd`` and `pathEndOrSingleSlash` were introduced to replace the former ``path("")``
   (see `PathMatcher infrastructure`_) [f0cbf25]_.


.. [4d787dc] `remove superfluous RequestContext::complete overloads <http://github.com/spray/spray/commit/4d787dc>`_
.. [1480e73] `improve PathMatcher infrastructure <http://github.com/spray/spray/commit/1480e73>`_
.. [7a36de5] `CompletionMagnet: gone, streamlining completion API: accomplished <http://github.com/spray/spray/commit/7a36de5>`_
.. [d86cb80] `remove layer of *Aux classes by type aliases for simplicity <http://github.com/spray/spray/commit/d86cb80>`_
.. [b145ced] `upgrade to new ToResponseMarshaller, closes #293 <http://github.com/spray/spray/commit/b145ced>`_
.. [9c9b976] `AuthenticationFailedRejection now directly contains challenge headers to return, fixes #538 <http://github.com/spray/spray/commit/9c9b976>`_
.. [ab35761] `fix getFromDirectory and getFromResourceDirectory not working properly for URIs with encoded chars <http://github.com/spray/spray/commit/ab35761>`_
.. [e3defb4] `have encodeResponse automatically tie in autoChunkFileBytes <http://github.com/spray/spray/commit/e3defb4>`_
.. [9c11228] `small improvement of require and hrequire modifiers on directives <http://github.com/spray/spray/commit/9c11228>`_
.. [ead4a70] `Added detach directive which executes its inner route in a future. Removed detachTo directive. Fixes #240. <http://github.com/spray/spray/commit/ead4a70>`_
.. [8c91851] `PathMatcher.(flat)map => h(flat)map, introduce map/flatMap, fixes #274 <http://github.com/spray/spray/commit/8c91851>`_
.. [034779d] `Render WWW-Authenticate header also for rejected credentials, fixes #188 <http://github.com/spray/spray/commit/034779d>`_
.. [ee7fe47] `redefine PathMatchers.Empty as PathMatchers.Neutral with explicit type annotation, fixes #339 <http://github.com/spray/spray/commit/ee7fe47>`_
.. [1326046] `move UserPassAuthenticator.cached to CachedUserPassAuthenticator.apply, fixes #352 <http://github.com/spray/spray/commit/1326046>`_
.. [3ff3471] `change PathMatcher.apply, add PathMatcher.provide method, cosmetic improvements <http://github.com/spray/spray/commit/3ff3471>`_
.. [8ee49d7] `add PathMatcher::repeated modifier, closes #636 <http://github.com/spray/spray/commit/8ee49d7>`_
.. [f0cbf25] `add pathEnd and pathEndOrSingleSlash directive, closes #628 <http://github.com/spray/spray/commit/f0cbf25>`_

spray-httpx
~~~~~~~~~~~

(TODO)

 - [ae17d18]_
 - [9d27559]_
 - [fad2ff2]_
 - [f8f5b6d]_
 - [ebaa580]_
 - [ebe3e97]_
 - [dd51be5]_
 - [f5b1535]_
 - [adf9170]_
 - [f5997f8]_

.. [ae17d18] `create FormFile as an easy way to access uploaded file information for forms, fixes #327 <http://github.com/spray/spray/commit/ae17d18>`_
.. [9d27559] `rename BodyPart.getName -> BodyPart.name, add BodyPart.dispositionParameterValue <http://github.com/spray/spray/commit/9d27559>`_
.. [fad2ff2] `polish MediaType model, fix tests, smaller improvements <http://github.com/spray/spray/commit/fad2ff2>`_
.. [f8f5b6d] `support content negotiation, fixes #167 <http://github.com/spray/spray/commit/f8f5b6d>`_
.. [ebaa580] `enable FEOU and FSOD to be interchanged in the usual cases, fixes #426 <http://github.com/spray/spray/commit/ebaa580>`_
.. [ebe3e97] `remove MetaUnmarshallers.scala, fold only member into FormDataUnmarshallers.scala <http://github.com/spray/spray/commit/ebe3e97>`_
.. [dd51be5] `change default charset for application/x-www-form-urlencoded to utf8, fixes #526 <http://github.com/spray/spray/commit/dd51be5>`_
.. [f5b1535] `decode should remove Content-Encoding header from message <http://github.com/spray/spray/commit/f5b1535>`_
.. [adf9170] `move unmarshal and unmarshalUnsafe to Unmarshaller and add unmarshaller method <http://github.com/spray/spray/commit/adf9170>`_
.. [f5997f8] `flexibilize RequestBuilding and ResponseTransformation by generalizing the ~> operator <http://github.com/spray/spray/commit/f5997f8>`_

spray-io
~~~~~~~~

(TODO)

 - [01c4aa9]_
 - [5f23219]_
 - [76345ba]_
 - [2c77d8f]_

.. [01c4aa9] `major refactoring of SslTlsSupport, fixes #544 <http://github.com/spray/spray/commit/01c4aa9>`_
.. [5f23219] `improve DynamicPipelines trait <http://github.com/spray/spray/commit/5f23219>`_
.. [76345ba] `abort connection on idle-timeout, fixes #539 <http://github.com/spray/spray/commit/76345ba>`_
.. [2c77d8f] `add support for compound write commands (Tcp.CompoundWrite) <http://github.com/spray/spray/commit/2c77d8f>`_


spray-testkit
~~~~~~~~~~~~~

(TODO)

 - [6a99cb7]_
 - [72c9397]_
 - [680fde0]_
 - [3b4ac55]_

.. [6a99cb7] `move result.awaitResult call from injectIntoRoute into check, fixes #205 <http://github.com/spray/spray/commit/6a99cb7>`_
.. [72c9397] `in RouteTests always convert URIs into absolute ones, fixes #464 <http://github.com/spray/spray/commit/72c9397>`_
.. [680fde0] `enable custom ExceptionHandlers in routing tests <http://github.com/spray/spray/commit/680fde0>`_
.. [3b4ac55] `small clean-up, remove duplication with httpx RequestBuilding <http://github.com/spray/spray/commit/3b4ac55>`_

spray-util
~~~~~~~~~~

(TODO)

 - [e234dd9]_
 - [b0b90b3]_
 - The settings infrastructure was reworked. ``XxxSettings.apply(config: Config)`` now takes the root config instead of
   the subconfig. For the old behavior use the new ``fromSubConfig`` instead [78d7e4a]_.

.. [e234dd9] `remove SprayActorLogging and UtilSettings, simplify LoggingContext, fixes #421 <http://github.com/spray/spray/commit/e234dd9>`_
.. [b0b90b3] `Swap Duration.Undefined by Duration.Inf, fixes #440 <http://github.com/spray/spray/commit/b0b90b3>`_
.. [78d7e4a] `improve *Settings infrastructure <http://github.com/spray/spray/commit/78d7e4a>`_
