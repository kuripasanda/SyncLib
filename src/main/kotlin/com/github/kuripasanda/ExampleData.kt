package com.github.kuripasanda

import kotlinx.serialization.Serializable

@Serializable
data class ExampleData(
    val id: Int,
    val message: String,
)