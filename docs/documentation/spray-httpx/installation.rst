Installation
============

The :ref:`maven-repo` chapter contains all the info about how to pull *spray-httpx* into your classpath.

Afterwards use the following imports to bring all relevant identifiers into scope:

- ``import spray.httpx.encoding._`` for everything related to (de)compression
- ``import spray.httpx.marshalling._`` for everything related to marshalling
- ``import spray.httpx.unmarshalling._`` for everything related to unmarshalling
- ``import spray.httpx.RequestBuilding`` for ``RequestBuilding``
- ``import spray.httpx.ResponseTransformation`` for ``ResponseTransformation``
- ``import spray.httpx.SprayJsonSupport`` for ``SprayJsonSupport``
- ``import spray.httpx.LiftJsonSupport`` for ``LiftJsonSupport``
- ``import spray.httpx.TwirlSupport`` for ``TwirlSupport``