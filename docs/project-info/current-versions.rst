.. _current-versions:

Current Version(s)
==================

Since *spray* heavily depends on Akka_ its releases are usually closely tied to specific Akka versions.


0.9.0
-----

This version targets Scala 2.9.x and Akka 1.3.x and is considered stable.
Its documentation doesn't live here but in the (old) `github wiki`_.


1.0/1.1
-------

Since the Scala universe is currently shifting from Scala 2.9.2 to Scala 2.10 the next *spray* release will be
a double release, targeting both current Scala (and Akka) versions at the same time.

However, at present *spray* 1.0/1.1 is not yet quite completed, the current milestone release is |1.0| or |1.1|,
depending on what Scala/Akka version you are targeting.

.. rst-class:: wide

- | *spray* |1.0| is built against Scala 2.9.2 and Akka 2.0.4.
  | It's sources live in the `release-1.0.x branch`_ of the *spray* repository.

- | *spray* |1.1| is built against Scala 2.10.0-RC3 and Akka 2.1.0-RC3.
  | It's sources live in the `master branch`_ of the *spray* repository.

For information about where to find the *spray* artifacts please check out the :ref:`maven-repo` chapter.

.. |1.0| replace:: **1.0-M6**
.. |1.1| replace:: **1.1-M6**


Nightly Builds
--------------

If you'd like to have access to the most recent changes and additions without having to build *spray* yourself you can
rely on the nightly builds, which we are currently publishing for the `master branch`_ and the `release-1.0.x branch`_
of the *spray* repository. Every day shortly past midnight UTC a new build is made available unless the respective
branch has not seen any new commits since the last build.

In order to help you identify the exact commit from which a build is cut every artifact directory includes a
``commit-<version>.html`` file containing the commit hash with a link to the commit on github.

Nightly builds are available from the http://nightlies.spray.io repository.


.. note:: In order to keep things manageable for us all sample snippets in the documentation on this site currently
   target the `master branch`_, i.e. the latest Nightly of *spray* 1.1. However, the differences between 1.0 and 1.1
   mainly concern the required imports, so you should generally have no problem to run them under Scala 2.9 + Akka 2.0 +
   *spray* 1.0 with only minimal changes.


.. _scala: http://scala-lang.org
.. _akka: http://akka.io
.. _github wiki: https://github.com/spray/spray/wiki
.. _release-1.0.x branch: https://github.com/spray/spray/tree/release-1.0.x
.. _master branch: https://github.com/spray/spray