package io.github.ganyuke.peoplehunt.core.events.models

// for entity death tracking
sealed class KillCause {
    data class KilledByPlayer(val killer: MatchPlayer) : KillCause()
    data class KilledByEntity(val entityIdentifier: String) : KillCause()
    data object Environmental : KillCause()
    data object Unknown : KillCause()
}