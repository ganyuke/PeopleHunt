package io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models

import io.github.ganyuke.peoplehunt.core.events.ReportableEvent
import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.core.events.models.MatchPlayer
import io.github.ganyuke.peoplehunt.core.services.core.models.MatchOutcome
import kotlin.time.Instant
import kotlin.uuid.Uuid

data class FinalizedMetadata(
    val endedAt: Instant,
    val outcome: MatchOutcome,
    val durationTicks: Int
)

data class ReportSession(
    val matchId: Uuid = Uuid.random(),
    val startedAt: Instant,
    val runner: MatchPlayer,
    val hunters: List<MatchPlayer>,

    val projectiles: List<EventFrame> = emptyList(),
    val snapshots: List<EventFrame> = emptyList(),
    val events: List<EventFrame> = emptyList(),
) {
    val empty get() = projectiles.isEmpty() && snapshots.isEmpty() && events.isEmpty()

    val latestTick: Int
        get() = maxOf(
            projectiles.lastOrNull()?.tick ?: 0,
            snapshots.lastOrNull()?.tick ?: 0,
            events.lastOrNull()?.tick ?: 0
        )

    fun withEvent(event: ReportableEvent): ReportSession {
        val frame = EventFrame(event.tick, event.occurredAt, event.payload)
        return when (event.payload) {
            is ReportablePayload.PlayerMovedByBlock -> this
            is ReportablePayload.ProjectileLaunched,
            is ReportablePayload.ProjectileMoved,
            is ReportablePayload.ProjectileHit -> copy(projectiles = projectiles + frame)
            is ReportablePayload.PlayerSnapshotChanged -> copy(snapshots = snapshots + frame)
            else -> copy(events = events + frame)
        }
    }

    fun drain(): Pair<ReportSession, FrameBatch> {
        return copy(
            projectiles = emptyList(),
            snapshots = emptyList(),
            events = emptyList()
        ) to FrameBatch(projectiles, snapshots, events)
    }
}