package io.github.ganyuke.peoplehunt.core.events.models

import kotlinx.serialization.Serializable

@Serializable
data class ActivePotionEffect(
    val type: String,
    val amplifier: Int,
    val duration: Int,
)
