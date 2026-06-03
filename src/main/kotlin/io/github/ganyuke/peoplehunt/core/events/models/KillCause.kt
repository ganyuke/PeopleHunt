package io.github.ganyuke.peoplehunt.core.events.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// for entity death tracking
@Serializable
sealed class KillCause {
    @Serializable @SerialName("KilledByPlayer")
    data class KilledByPlayer(val killer: MatchPlayer) : KillCause()

    @Serializable @SerialName("KilledByEntity")
    data class KilledByEntity(val entityIdentifier: String) : KillCause()

    @Serializable @SerialName("Environmental")
    data object Environmental : KillCause()

    @Serializable @SerialName("Unknown")
    data object Unknown : KillCause()
}