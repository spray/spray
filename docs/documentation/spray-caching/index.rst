.. _spray-caching:

spray-caching
=============

*spray-caching* provides a lightweight and fast in-memory caching functionality based on Akka Futures and
concurrentlinkedhashmap_. The primary use-case is the "wrapping" of an expensive operation with a caching layer that,
based on a certain key of type ``K``, runs the wrapped operation only once and returns the the cached value for all
future accesses for the same key (as long as the respective entry has not expired).

The central idea of a *spray-cachine* cache is to not store the actual values of type ``T`` themselves in the cache
but rather corresponding Akka Futures, i.e. instances of type ``Future[T]``. This approach has the advantage of nicely
taking care of the thundering herds problem where many requests to a particular cache key (e.g. a resource URI) arrive
before the first one could be completed. Normally (without special guarding techniques, like so-called "cowboy" entries)
this can cause many requests to compete for system resources while trying to compute the same result thereby greatly
reducing overall system performance. When you use a *spray-caching* cache the very first request that arrives for a
certain cache key causes a future to be put into the cache which all later requests then "hook into". As soon as the
first request completes all other ones complete as well. This minimizes processing time and server load for all requests.


Dependencies
------------

Apart from the Scala library (see :ref:`Current Versions` chapter) *spray-caching* depends on

- :ref:`spray-util`
- concurrentlinkedhashmap_
- akka-actor 2.1.x (with 'provided' scope, i.e. you need to pull it in yourself)


Installation
------------

The :ref:`maven-repo` chapter contains all the info about how to pull *spray-caching* into your classpath.

Afterwards just ``import spray.caching._`` to bring all relevant identifiers into scope.


The `Cache` Interface
---------------------

All *spray-caching* cache implementations implement the Cache_ trait, which allow you to interact with the cache
in through six methods:

.. rst-class:: wide

- ``def apply(key: Any)(expr: => V): Future[V]`` wraps an "expensive" expression with caching support.

- ``def apply(key: Any)(future: => Future[V]): Future[V]`` is similar, but allows the expression to produce
  the future itself.

- ``def apply(key: Any)(func: Promise[V] => Unit): Future[V]`` provides a "push-style" alternative.

- ``def get(key: Any): Option[Future[V]]`` retrieves the future instance that is currently in the cache for
  the given key. Returns ``None`` if the key has no corresponding cache entry.

- ``def remove(key: Any): Option[Future[V]]`` removes the cache item for the given key.
  Returns the removed item if it was found (and removed).

- ``def clear()`` clears the cache by removing all entries.

Note that the ``apply`` overloads require an implicit ``ExecutionContext`` to be in scope.


Example
-------

.. includecode:: code/docs/CachingExamplesSpec.scala
   :snippet: example-1


Cache Implementations
---------------------

*spray-caching* comes with two implementations of the Cache_ interface, `SimpleLruCache and ExpiringLruCache`_,
both featuring last-recently-used cache eviction semantics and both internally wrapping a concurrentlinkedhashmap_.
They difference between the two only consists of whether they support time-based entry expiration or not.

The easiest way to construct a cache instance is via the ``apply`` method of the ``LruCache`` object, which has the
following signature and creates a new ``ExpiringLruCache`` or ``SimpleLruCache`` depending on whether a non-zero and
finite ``timeToLive`` and/or ``timeToIdle`` is set or not:

.. includecode:: /../spray-caching/src/main/scala/spray/caching/LruCache.scala
   :snippet: source-quote-LruCache-apply


SimpleLruCache
~~~~~~~~~~~~~~

This cache implementation has a defined maximum number of entries it can store. After the maximum capacity is reached
new entries cause old ones to be evicted in a last-recently-used manner, i.e. the entries that haven't been accessed
for the longest time are evicted first.

ExpiringLruCache
~~~~~~~~~~~~~~~~

This implementation has the same limited capacity behavior as the ``SimpleLruCache`` but in addition supports
time-to-live as well as time-to-idle expiration.
The former provides an upper limit to the time period an entry is allowed to remain in the cache while the latter
limits the maximum time an entry is kept without having been accessed. If both values are non-zero the time-to-live
has to be strictly greater than the time-to-idle.

.. note:: Expired entries are only evicted upon next access (or by being thrown out by the capacity constraint), so
   they might prevent gargabe collection of their values for longer than expected.


.. _Cache: https://github.com/spray/spray/blob/master/spray-caching/src/main/scala/spray/caching/Cache.scala
.. _SimpleLruCache and ExpiringLruCache: https://github.com/spray/spray/blob/master/spray-caching/src/main/scala/spray/caching/LruCache.scala
.. _concurrentlinkedhashmap: http://code.google.com/p/concurrentlinkedhashmap/