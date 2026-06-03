package io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models

import io.github.ganyuke.peoplehunt.core.events.models.MatchPlayer
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class ReportDocument(
    @Contextual val matchId: Uuid,
    @Contextual val startedAt: Instant,
    val runner: MatchPlayer,
    val hunters: List<MatchPlayer>,
    val durationTicks: Int,
    val projectiles: List<EventFrame>,
    val snapshots: List<EventFrame>,
    val events: List<EventFrame>,
)