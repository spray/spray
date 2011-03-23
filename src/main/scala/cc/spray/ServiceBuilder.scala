package cc.spray

import builders._

trait ServiceBuilder
        extends CachingBuilders
        with DetachedBuilders
        with FileResourceDirectoryBuilders
        with FilterBuilders
        with MiscBuilders
        with ParameterBuilders
        with PathBuilders
        with SimpleFilterBuilders
        with UnMarshallingBuilders