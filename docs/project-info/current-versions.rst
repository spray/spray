.. _Current Versions:

Current Versions
================

Since *spray* heavily depends on Akka_ its releases are usually closely tied to specific Akka versions.


0.9.0
-----

This version targets Scala 2.9.x and Akka 1.3.x and is therefore not recommended for new projects anymore.
Its documentation doesn't live here but in the (old) `github wiki`_.


1.0.1 / 1.1.1 / 1.2.1 / 1.3.1
-----------------------------

The current and stable *spray* release is a quadrupel release,
targeting Scala 2.9 and Scala 2.10 as well as four Akka versions at the same time.

Please choose |1.0|, |1.1|, |1.2| or |1.3| depending on what Scala/Akka version you are targeting:

.. rst-class:: wide

- | *spray* |1.0| is built against Scala 2.9.3 and Akka 2.0.5.
  | It's sources live in the `release/1.0`_ branch of the *spray* repository.

- | *spray* |1.1| is built against Scala 2.10.3 and Akka 2.1.4.
  | It's sources live in the `release/1.1`_ branch of the *spray* repository.

- | *spray* |1.2| is built against Scala 2.10.3 and Akka 2.2.4.
  | It's sources live in the `release/1.2`_ branch of the *spray* repository.
  | (Please note that Akka 2.2.3 or later is *required*, earlier Akka versions will *not* work!)

- | *spray* |1.3| is built against Scala 2.10.3 and Akka 2.3.0 as well as Scala 2.11.0 and Akka 2.3.2.
  | It's sources live in the `release/1.3`_ and `release/1.3_2.11`_ branches of the *spray* repository.
  | **NOTE**: Due to a few missing upstream dependencies *spray* |1.3| for Scala 2.11 is currently only
  | available as a preview release (without the JSON support for *lift-json* and *play-json*).
  | Use ``"io.spray" %% "spray-xxxxx" % "1.3.1-20140423"`` as library dependency in SBT.

For information about where to find the *spray* artifacts please check out the :ref:`maven-repo` chapter.

.. |1.0| replace:: **1.0.1**
.. |1.1| replace:: **1.1.1**
.. |1.2| replace:: **1.2.1**
.. |1.3| replace:: **1.3.1**


Nightly Builds
--------------

If you'd like to have access to the most recent changes and additions without having to build *spray* yourself you can
rely on the nightly builds, which we are currently publishing for the `release/1.0`_, `release/1.1`_, `release/1.2`_ and
`release/1.3`_ branches of the *spray* repository. Every day shortly past midnight UTC a new build is made available
unless the respective branch has not seen any new commits since the last build.

In order to help you identify the exact commit from which a build is cut every artifact directory includes a
``commit-<version>.html`` file containing the commit hash with a link to the commit on github.

Nightly builds are available from the http://nightlies.spray.io repository.

.. _akka: http://akka.io
.. _github wiki: https://github.com/spray/spray/wiki
.. _master: https://github.com/spray/spray
.. _release/1.0: https://github.com/spray/spray/tree/release/1.0
.. _release/1.1: https://github.com/spray/spray/tree/release/1.1
.. _release/1.2: https://github.com/spray/spray/tree/release/1.2
.. _release/1.3: https://github.com/spray/spray/tree/release/1.3
.. _release/1.3_2.11: https://github.com/spray/spray/tree/release/1.3_2.11
