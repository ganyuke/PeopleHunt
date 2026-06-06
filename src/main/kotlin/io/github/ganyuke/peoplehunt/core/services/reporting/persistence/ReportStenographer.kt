package io.github.ganyuke.peoplehunt.core.services.reporting.persistence

import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.events.MatchEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportableEvent
import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.core.ports.LoggerPort
import io.github.ganyuke.peoplehunt.core.ports.SchedulerPort
import io.github.ganyuke.peoplehunt.core.ports.TaskHandle
import io.github.ganyuke.peoplehunt.core.services.core.MatchEngine
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.EventFrame
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.FrameBatch
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.MatchOpenSession
import io.github.ganyuke.peoplehunt.core.utils.PhConfig
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max

class ReportStenographer(
    private val outbound: MatchEventBus,
    private val scheduler: SchedulerPort,
    private val logger: LoggerPort,
    private val storage: ReportStorage,
    private val config: PhConfig,
) {
    private val scopeJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + scopeJob)
    private val storageMutex = Mutex()
    private val projectiles = mutableListOf<EventFrame>()
    private val snapshots = mutableListOf<EventFrame>()
    private val events = mutableListOf<EventFrame>()

    private var sessionState = ReportSessionState.CLOSED
    private var matchId: Uuid? = null
    private var matchStartTime: Instant? = null
    private var openSession: MatchOpenSession? = null
    private var pendingFinalize: PendingFinalize? = null
    private var lastTick = 0

    private var flushScheduler: ReportFlushScheduler? = null
    private var flushTask: TaskHandle? = null

    private data class PendingFinalize(
        val endedAt: Instant,
        val outcome: MatchEngine.MatchOutcome,
        val durationTicks: Int,
    )

    fun blockReason(): ReportSessionBlockReason? = when (sessionState) {
        ReportSessionState.CLOSED -> null
        ReportSessionState.RECORDING -> ReportSessionBlockReason.SESSION_ALREADY_ACTIVE
        ReportSessionState.OPEN_FAILED -> ReportSessionBlockReason.DATABASE_OPEN_FAILED
        ReportSessionState.FINALIZE_PENDING -> ReportSessionBlockReason.FINALIZE_PENDING
    }

    fun onMatchEvent(event: MatchEvent) {
        when (event) {
            is MatchEvent.MatchStart -> beginSession(event)
            is MatchEvent.MatchEnd -> endSession(event)
            else -> {}
        }
    }

    fun onReportableEvent(event: ReportableEvent) {
        if (sessionState == ReportSessionState.CLOSED) return
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

    suspend fun manualFlush(): ReportOpResult = when (sessionState) {
        ReportSessionState.CLOSED -> ReportOpResult.Err(ReportOpFailure.NO_OPEN_SESSION)
        ReportSessionState.RECORDING -> flushRecording(manual = true)
        ReportSessionState.OPEN_FAILED -> recoverOpenFailed(manual = true)
        ReportSessionState.FINALIZE_PENDING -> finalizePending(manual = true)
    }

    fun clear(): ReportOpResult {
        if (sessionState == ReportSessionState.CLOSED) {
            return ReportOpResult.Err(ReportOpFailure.NO_OPEN_SESSION)
        }
        stopFlushScheduler()
        storage.closeActive()
        resetBuffers()
        openSession = null
        pendingFinalize = null
        matchId = null
        matchStartTime = null
        sessionState = ReportSessionState.CLOSED
        scheduler.runOnMainThread {
            logger.info("Report session cleared; in-memory data discarded.")
        }
        return ReportOpResult.Ok("In-memory report data cleared.")
    }

    fun shutdown() {
        stopFlushScheduler()
        storage.closeActive()
        scopeJob.cancel()
    }

    private fun beginSession(event: MatchEvent.MatchStart) {
        resetBuffers()
        lastTick = 0
        val id = Uuid.random()
        val startedAt = Clock.System.now()
        matchId = id
        matchStartTime = startedAt
        openSession = MatchOpenSession(
            matchId = id,
            startedAt = startedAt,
            runner = event.runner,
            hunters = event.hunters.toList(),
        )
        sessionState = ReportSessionState.RECORDING

        scope.launch {
            runCatching { storageMutex.withLock { storage.openMatch(openSession!!) } }
                .onSuccess {
                    scheduler.runOnMainThread {
                        startFlushScheduler(startedAt)
                        logger.info("Opened report database for match ${id.toCompactLog()}.")
                    }
                }
                .onFailure { cause ->
                    sessionState = ReportSessionState.OPEN_FAILED
                    stopFlushScheduler()
                    reportError(
                        cause,
                        "Failed to open report database",
                        "Failed to open report database — run /ph report flush to retry",
                    )
                }
        }
    }

    private fun endSession(event: MatchEvent.MatchEnd) {
        val result = event.result
        pendingFinalize = PendingFinalize(
            endedAt = result.endedAt,
            outcome = result.outcome,
            durationTicks = lastTick,
        )
        stopFlushScheduler()

        scope.launch {
            when (sessionState) {
                ReportSessionState.OPEN_FAILED, ReportSessionState.RECORDING -> {
                    val openResult = if (sessionState == ReportSessionState.OPEN_FAILED) {
                        runCatching { storageMutex.withLock { storage.openMatch(openSession!!) } }
                    } else {
                        Result.success(Unit)
                    }
                    openResult
                        .onFailure { cause ->
                            sessionState = ReportSessionState.FINALIZE_PENDING
                            reportError(
                                cause,
                                "Match ended but report could not be opened",
                                "Match ended but report was not saved — run /ph report flush",
                            )
                        }
                        .onSuccess {
                            flushAndFinalize(manual = false)
                        }
                }

                ReportSessionState.FINALIZE_PENDING -> flushAndFinalize(manual = false)
                ReportSessionState.CLOSED -> {}
            }
        }
    }

    private suspend fun flushRecording(manual: Boolean): ReportOpResult {
        val batch = drainBatch()
        if (batch.isEmpty()) return ReportOpResult.Err(ReportOpFailure.NOTHING_TO_FLUSH)
        return appendBatch(batch, manual = manual, recovery = false)
    }

    private suspend fun recoverOpenFailed(manual: Boolean): ReportOpResult {
        val session = openSession ?: return ReportOpResult.Err(ReportOpFailure.NO_OPEN_SESSION)
        val batch = drainBatch()
        if (batch.isEmpty() && !manual) return ReportOpResult.Err(ReportOpFailure.NOTHING_TO_FLUSH)

        return runCatching { storageMutex.withLock { storage.openMatch(session) } }
            .fold(
                onSuccess = {
                    val flushBatch = if (batch.isEmpty()) currentBatch() else batch
                    if (flushBatch.isEmpty()) {
                        sessionState = ReportSessionState.RECORDING
                        scheduler.runOnMainThread {
                            val anchor = matchStartTime
                            if (anchor != null) resumeFlushScheduler(anchor)
                            logger.info("Opened report database for match ${session.matchId.toCompactLog()}; no frames to flush yet.")
                        }
                        ReportOpResult.Ok("Report database opened.")
                    } else {
                        val result = appendBatch(flushBatch, manual = manual, recovery = true)
                        if (result is ReportOpResult.Ok) {
                            sessionState = ReportSessionState.RECORDING
                            scheduler.runOnMainThread {
                                val anchor = matchStartTime
                                if (anchor != null) resumeFlushScheduler(anchor)
                            }
                        }
                        result
                    }
                },
                onFailure = { cause ->
                    reportError(
                        cause,
                        "Failed to open report database",
                        "Failed to open report database — run /ph report flush to retry",
                    )
                    ReportOpResult.Err(ReportOpFailure.WRITE_FAILED, cause)
                },
            )
    }

    private suspend fun finalizePending(manual: Boolean): ReportOpResult {
        val id = matchId ?: return ReportOpResult.Err(ReportOpFailure.NO_OPEN_SESSION)
        val finalize = pendingFinalize ?: return ReportOpResult.Err(ReportOpFailure.NOTHING_TO_FLUSH)
        val batch = drainBatch()

        if (!batch.isEmpty()) {
            val appendResult = appendBatch(batch, manual = manual, recovery = false)
            if (appendResult is ReportOpResult.Err) return appendResult
        }

        return runCatching {
            storageMutex.withLock {
                storage.finalizeMatch(id, finalize.endedAt, finalize.outcome, finalize.durationTicks)
            }
        }.fold(
            onSuccess = {
                scheduler.runOnMainThread {
                    sessionState = ReportSessionState.CLOSED
                    outbound.post(MatchEvent.ReportPersisted(id))
                    resetBuffers()
                    openSession = null
                    pendingFinalize = null
                    matchId = null
                    matchStartTime = null
                    logger.info("Finalized report for match ${id.toCompactLog()}.")
                }
                ReportOpResult.Ok("Report finalized for match ${id.toCompactLog()}.")
            },
            onFailure = { cause ->
                sessionState = ReportSessionState.FINALIZE_PENDING
                reportError(
                    cause,
                    "Failed to finalize report",
                    "Match ended but report was not saved — run /ph report flush",
                )
                ReportOpResult.Err(ReportOpFailure.WRITE_FAILED, cause)
            },
        )
    }

    private suspend fun flushAndFinalize(manual: Boolean) {
        val batch = drainBatch()
        if (!batch.isEmpty()) {
            val appendResult = appendBatch(batch, manual = manual, recovery = false)
            if (appendResult is ReportOpResult.Err) {
                sessionState = ReportSessionState.FINALIZE_PENDING
                return
            }
        }
        finalizePending(manual = manual)
    }

    private suspend fun appendBatch(batch: FrameBatch, manual: Boolean, recovery: Boolean): ReportOpResult {
        val id = matchId ?: return ReportOpResult.Err(ReportOpFailure.NO_OPEN_SESSION)
        val flushTime = Clock.System.now()
        return runCatching { storageMutex.withLock { storage.appendFlush(id, batch, flushTime) } }
            .fold(
                onSuccess = {
                    clearDrained(batch)
                    val message = buildString {
                        append("Flushed report batch for match ${id.toCompactLog()}")
                        append(" (snapshots=${batch.snapshots.size}, projectiles=${batch.projectiles.size}, events=${batch.events.size})")
                        if (recovery) append(" — database recovered")
                    }
                    scheduler.runOnMainThread {
                        logger.info(message)
                    }
                    ReportOpResult.Ok(message)
                },
                onFailure = { cause ->
                    restoreDrained(batch)
                    reportError(
                        cause,
                        "Report flush failed",
                        "Report flush failed — data retained; retry at next interval or run /ph report flush",
                    )
                    ReportOpResult.Err(ReportOpFailure.WRITE_FAILED, cause)
                },
            )
    }

    private fun attemptAutoFlush() {
        if (sessionState != ReportSessionState.RECORDING) return
        scope.launch {
            val batch = drainBatch()
            if (batch.isEmpty()) return@launch
            appendBatch(batch, manual = false, recovery = false)
        }
    }

    private fun drainBatch(): FrameBatch {
        val batch = FrameBatch(projectiles.toList(), snapshots.toList(), events.toList())
        projectiles.clear()
        snapshots.clear()
        events.clear()
        return batch
    }

    private fun currentBatch(): FrameBatch =
        FrameBatch(projectiles.toList(), snapshots.toList(), events.toList())

    private fun clearDrained(batch: FrameBatch) {
        // lists already cleared in drainBatch; nothing to do
    }

    private fun restoreDrained(batch: FrameBatch) {
        projectiles.addAll(0, batch.projectiles)
        snapshots.addAll(0, batch.snapshots)
        events.addAll(0, batch.events)
    }

    private fun resetBuffers() {
        projectiles.clear()
        snapshots.clear()
        events.clear()
        lastTick = 0
    }

    private fun startFlushScheduler(anchor: Instant) {
        val schedulerInstance = flushScheduler ?: ReportFlushScheduler(
            config.reportFlushInterval,
            scope,
            ::attemptAutoFlush,
        ).also { flushScheduler = it }
        flushTask = schedulerInstance.start(anchor)
    }

    private fun resumeFlushScheduler(anchor: Instant) {
        val schedulerInstance = flushScheduler ?: ReportFlushScheduler(
            config.reportFlushInterval,
            scope,
            ::attemptAutoFlush,
        ).also { flushScheduler = it }
        schedulerInstance.resume(anchor)
        flushTask = object : TaskHandle {
            override fun cancel() = schedulerInstance.stop()
        }
        attemptAutoFlush()
    }

    private fun stopFlushScheduler() {
        flushTask?.cancel()
        flushTask = null
        flushScheduler?.stop()
    }

    internal fun reportError(cause: Throwable, logMessage: String, operatorMessage: String) {
        scheduler.runOnMainThread {
            logger.error(logMessage, cause)
            outbound.post(MatchEvent.OperatorNotification("$operatorMessage: ${cause.message ?: "Unknown error"}"))
        }
    }

    private fun Uuid.toCompactLog(): String = toString().replace("-", "")
}
