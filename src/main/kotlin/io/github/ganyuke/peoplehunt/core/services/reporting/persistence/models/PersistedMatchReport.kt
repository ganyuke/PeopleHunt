package io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models

import io.github.ganyuke.peoplehunt.core.events.models.MatchPlayer
import io.github.ganyuke.peoplehunt.core.services.core.models.MatchOutcome
import kotlin.time.Instant
import kotlin.uuid.Uuid

data class PersistedMatchReport(
    val matchId: Uuid,
    val startedAt: Instant,
    val endedAt: Instant?,
    val durationTicks: Int?,
    val outcome: MatchOutcome?,
    val runner: MatchPlayer,
    val hunters: List<MatchPlayer>,
    val snapshotFrames: List<EventFrame>,
    val projectileFrames: List<EventFrame>,
    val eventFrames: List<EventFrame>,
)