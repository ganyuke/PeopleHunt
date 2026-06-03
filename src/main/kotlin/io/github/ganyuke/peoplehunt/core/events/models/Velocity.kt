package io.github.ganyuke.peoplehunt.core.events.models

import kotlinx.serialization.Serializable

@Serializable
data class Velocity(
    val x: Double,
    val y: Double,
    val z: Double,
)
