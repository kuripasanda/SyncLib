package com.github.kuripasanda.api.exception

import net.minecraft.resources.ResourceLocation

class RegistryAlreadyExistsException(
    val id: ResourceLocation,
    override val message: String = "Registry with id '$id' already exists."
): Exception()