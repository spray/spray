package cc.spray

import builders._

trait ServiceBuilder
        extends BasicBuilders
        with PathBuilders
        with ParameterBuilders
        with UnMarshallingBuilders
        with FileResourceDirectoryBuilders