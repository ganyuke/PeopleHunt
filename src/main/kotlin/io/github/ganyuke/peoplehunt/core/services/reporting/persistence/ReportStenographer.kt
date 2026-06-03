package io.github.ganyuke.peoplehunt.core.services.reporting.persistence

import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.events.MatchEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportableEvent
import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.core.ports.LoggerPort
import io.github.ganyuke.peoplehunt.core.ports.SchedulerPort
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.ReportDocument
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.EventFrame
import kotlinx.coroutines.*
import kotlin.math.max
import kotlin.uuid.Uuid

/**
 * Collects every [ReportableEvent] and serializes the full replay to JSON via
 * the provided [ReportStorage].
 */
class ReportStenographer(
    private val outbound: MatchEventBus,
    private val scheduler: SchedulerPort,
    private val logger: LoggerPort,
    private val sink: ReportStorage,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val projectiles = mutableListOf<EventFrame>()
    private val snapshots = mutableListOf<EventFrame>()
    private val events = mutableListOf<EventFrame>()
    private var lastTick = 0

    internal fun reportError(cause: Throwable) {
        scheduler.runOnMainThread {
            logger.error("ReplayWriter failed to write replay", cause)
            outbound.post(MatchEvent.OperatorNotification("ReplayWriter failed to write replay: ${cause.message ?: "Unknown error"}"))
        }
    }

    fun resetFrames() {
        projectiles.clear()
        snapshots.clear()
        events.clear()
    }

    fun onMatchEvent(event: MatchEvent) {
        when (event) {
            is MatchEvent.MatchStart -> {
                lastTick = 0
                resetFrames()
            }

            is MatchEvent.MatchEnd -> flush(event)
            else -> {}
        }
    }

    fun onReportableEvent(event: ReportableEvent) {
        val frame = EventFrame(event.tick, event.occurredAt, event.payload)
        when (event.payload) {
            is ReportablePayload.PlayerMovedByBlock -> {} // don't bother saving this, already covered by poll
            is ReportablePayload.ProjectileLaunched,
            is ReportablePayload.ProjectileMoved,
            is ReportablePayload.ProjectileHit -> projectiles += frame

            is ReportablePayload.PlayerSnapshotChanged -> snapshots += frame
            else -> events += frame
        }
        lastTick = max(lastTick, event.tick)
    }

    private fun flush(event: MatchEvent.MatchEnd) {
        val r = event.result
        val matchId = Uuid.random()
        val doc = ReportDocument(
            matchId = matchId,
            startedAt = r.startedAt,
            runner = r.runner,
            hunters = r.hunters.toList(),
            durationTicks = lastTick,
            projectiles = projectiles,
            snapshots = snapshots,
            events = events,
        )

        scope.launch {
            runCatching { sink.write(doc) }
                .onSuccess {
                    scheduler.runOnMainThread {
                        resetFrames()
                        logger.info("ReplayWriter successfully wrote match $matchId to disk.")
                    }
                }
                .onFailure(::reportError)
        }
    }

    fun shutdown() = scope.cancel()
}