package io.github.ganyuke.peoplehunt.core.services.core.models

import io.github.ganyuke.peoplehunt.core.events.models.MatchPlayer
import kotlin.time.Instant

sealed interface MatchState {
    val runner: MatchPlayer?
    val hunters: Set<MatchPlayer>

    data class Idle(
        override val runner: MatchPlayer?,
        override val hunters: Set<MatchPlayer> = emptySet(),
    ) : MatchState

    data class Primed(
        override val runner: MatchPlayer,
        override val hunters: Set<MatchPlayer>,
        val primedAt: Instant,
    ) : MatchState

    data class Active(
        override val runner: MatchPlayer,
        override val hunters: Set<MatchPlayer>,
        val startedAt: Instant,
    ) : MatchState

    data class Finished(
        override val runner: MatchPlayer,
        override val hunters: Set<MatchPlayer>,
        val startedAt: Instant,
        val endedAt: Instant,
        val outcome: MatchOutcome,
    ) : MatchState
}