Installation
============

The :ref:`maven-repo` chapter contains all the info about how to pull *spray-can* into your classpath.

Afterwards just ``import spray.routing._`` to bring all relevant identifiers into scope.

Also, if you are using *spray-routing* with Scala 2.9.x, make sure to include the following with your build definition::

  scalacOptions += "-Ydependent-method-types"

If you don't have *dependent-method-types* enabled you will be seeing compiler errors such as this::

    [error]  found   : spray.routing.directives.StandardRoute
    [error]  required: hac.In
    [error]             complete("PONG!")
    [error]                     ^
    [error] one error found
    [error] (compile:compile) Compilation failed

As of Scala 2.10 *dependent-method-types* is enabled by default, so no further configuration is required, but with
Scala 2.9.x you have to add the respective compiler flag yourself.