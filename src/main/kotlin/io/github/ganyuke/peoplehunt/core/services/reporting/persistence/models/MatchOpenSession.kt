package io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models

import io.github.ganyuke.peoplehunt.core.events.models.MatchPlayer
import kotlin.time.Instant
import kotlin.uuid.Uuid

data class MatchOpenSession(
    val matchId: Uuid,
    val startedAt: Instant,
    val runner: MatchPlayer,
    val hunters: List<MatchPlayer>,
)
