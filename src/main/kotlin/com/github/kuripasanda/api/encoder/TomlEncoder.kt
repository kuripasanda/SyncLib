package com.github.kuripasanda.api.encoder

import dev.eav.tomlkt.Toml
import kotlinx.serialization.KSerializer
import kotlinx.serialization.decodeFromString

object TomlEncoder {

    inline fun <reified T : Any> decodeFromString(text: String, serializer: KSerializer<T>): T {
        return Toml.decodeFromString<T>(text)
    }

    inline fun <reified T : Any> decodeFromString(text: String): T {
        return Toml.decodeFromString<T>(text)
    }

}