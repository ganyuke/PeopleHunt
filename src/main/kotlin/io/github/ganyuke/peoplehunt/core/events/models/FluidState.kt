package io.github.ganyuke.peoplehunt.core.events.models

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
sealed class FluidState {
    @Serializable @SerialName("Dry")
    data object Dry : FluidState()

    @Serializable @SerialName("SubmergedInWater")
    data class SubmergedInWater( // breath depletes when head below water
        @Contextual val since: Instant
    ) : FluidState()

    @Serializable @SerialName("InLava")
    data class InLava( // lava doesn't really have swimming
        @Contextual val since: Instant
    ) : FluidState()

    @Serializable @SerialName("SuffocatingInBlock")
    data class SuffocatingInBlock(
        @Contextual val since: Instant
    ) : FluidState()
}