Installation
============

The :ref:`maven-repo` chapter contains all the info about how to pull *spray-httpx* into your classpath.

Afterwards use the following imports to bring all relevant identifiers into scope:

- ``import cc.spray.httpx.encoding._`` for everything related to (de)compression
- ``import cc.spray.httpx.marshalling._`` for everything related to marshalling
- ``import cc.spray.httpx.unmarshalling._`` for everything related to unmarshalling
- ``import cc.spray.httpx.RequestBuilding`` for ``RequestBuilding``
- ``import cc.spray.httpx.ResponseTransformation`` for ``ResponseTransformation``
- ``import cc.spray.httpx.SprayJsonSupport`` for ``SprayJsonSupport``
- ``import cc.spray.httpx.LiftJsonSupport`` for ``LiftJsonSupport``
- ``import cc.spray.httpx.TwirlSupport`` for ``TwirlSupport``