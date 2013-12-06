.. _-anyParams-:

anyParams
=========

The ``anyParams`` directive allows to extract values both from query parameters and form fields.


Signature
---------

::

    def anyParams(params: <ParamDef[T_i]>*): Directive[T_0 :: ... T_i ... :: HNil]
    def anyParams(params: <ParamDef[T_0]> :: ... <ParamDef[T_i]> ... :: HNil): Directive[T_0 :: ... T_i ... :: HNil]

The signature shown is simplified and written in pseudo-syntax, the real signature uses magnets. [1]_ The type
``<ParamDef>`` doesn't really exist but consists of the syntactic variants as shown in the description and the examples
of the ``parameters`` directive.

.. [1] See `The Magnet Pattern`_ for an explanation of magnet-based overloading.
.. _`The Magnet Pattern`: /blog/2012-12-13-the-magnet-pattern/

Description
-----------

The directives combines the functionality from :ref:`-parameters-` and :ref:`-formFields-` in one directive. To be able
to unmarshal a parameter to a value of a specific type (e.g. with ``as[Int]``) you need to fulfill the requirements
as explained both for :ref:`-parameters-` and :ref:`-formFields-`.

There's a singular version, :ref:`-anyParam-`.

Example
-------

.. includecode:: ../code/docs/directives/AnyParamDirectivesExamplesSpec.scala
   :snippet: example-1

