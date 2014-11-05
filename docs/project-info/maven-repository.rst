.. _maven-repo:

Maven Repository
================

The latest *spray* releases are available from `Maven Central`_, so no special resolver should be required to be able
to pull them in. In addition, older artifacts (including milestones and RCs) are hosted in this repository:

  http://repo.spray.io

If you use SBT_ you'll want to add the following resolver::

  resolvers += "spray repo" at "http://repo.spray.io"

Nightly builds are available from http://nightlies.spray.io, to use them add this resolver::

  resolvers += "spray nightlies repo" at "http://nightlies.spray.io"

.. _Maven Central: http://search.maven.org/


Artifact Naming
---------------

All *spray* artifacts follow this naming scheme:

:Group ID:    ``io.spray``
:Artifact ID: Module Name
:Version:     Release Version


So, for expressing a dependency on a *spray* module with SBT_ you'll want to add something like this
to your project settings::

  libraryDependencies += "io.spray" % "spray-can" % "1.0"

Make sure to replace the artifact name and version number with the one you are targeting! (see :ref:`Current Versions`)


.. _SBT: http://www.scala-sbt.org/
