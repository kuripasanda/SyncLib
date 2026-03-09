package com.github.kuripasanda.api.obfuscate

import kotlinx.serialization.Serializable
import net.minecraft.resources.ResourceLocation

@Serializable
data class ObfuscatedResourceLocation(
    val namespace: String,
    val path: String
) {

    companion object {
        fun fromString(string: String): ObfuscatedResourceLocation {
            val parts = string.split(":")
            if (parts.size != 2) {
                throw IllegalArgumentException("Invalid resource location format: $string")
            }
            return ObfuscatedResourceLocation(parts[0], parts[1])
        }

        fun fromResourceLocation(resourceLocation: ResourceLocation): ObfuscatedResourceLocation {
            return ObfuscatedResourceLocation(resourceLocation.namespace, resourceLocation.path)
        }
    }

    override fun toString(): String {
        return "$namespace:$path"
    }
}