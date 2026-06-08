package io.github.ganyuke.peoplehunt.core.services.reporting.persistence.stenography

import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.events.ReportableEvent
import io.github.ganyuke.peoplehunt.core.ports.inbound.StenographerPort
import io.github.ganyuke.peoplehunt.core.ports.outbound.LoggerPort
import io.github.ganyuke.peoplehunt.core.ports.outbound.SchedulerPort
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.ReportOpFailure
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.ReportOpResult
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.ReportSessionBlockReason
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.FinalizedMetadata
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.ReportSession
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.ReportStorage
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.stenography.ReportStenographer.ReportState.Closed
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.stenography.ReportStenographer.ReportState.Running
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.stenography.ReportStenographer.ReportState.Setup
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.stenography.ReportStenographer.ReportState.Teardown
import io.github.ganyuke.peoplehunt.core.utils.PhConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant

class ReportStenographer(
    val logger: LoggerPort,
    val storage: ReportStorage,
    val scheduler: SchedulerPort,
    val config: PhConfig
) : StenographerPort {
    private interface WithSession {
        val session: ReportSession
    }

    private interface WithJob {
        val ioJob: Job
    }

    private sealed interface ReportState {
        // init state, finished final write state
        object Closed : ReportState

        sealed interface Setup : ReportState, WithSession {
            override val session: ReportSession

            // match start state, creating DB state
            data class Initializing(
                override val session: ReportSession,
                override val ioJob: Job
            ) : Setup, WithJob

            // match start state, open DB failed, allow manual flush
            data class InitializingFailed(
                override val session: ReportSession
            ) : Setup
        }

        sealed interface Running : ReportState, WithSession {
            override val session: ReportSession
            val autoflushJob: Job?

            // successfully opened DB state, primary state
            data class Open(
                override val session: ReportSession,
                override val autoflushJob: Job?
            ) : Running

            // open state is actively writing to DB
            data class Writing(
                override val session: ReportSession,
                override val autoflushJob: Job?,
                override val ioJob: Job
            ) : Running, WithJob
        }

        sealed interface Teardown : ReportState, WithSession {
            override val session: ReportSession
            val metadata: FinalizedMetadata

            // state machine transitioned was told to finalize and I/O in progress
            data class FinalizingPending(
                override val session: ReportSession,
                override val metadata: FinalizedMetadata,
                override val ioJob: Job // from the previous I/O operation
            ) : Teardown, WithJob

            // match end state, awaiting final flush to disk
            data class Finalizing(
                override val session: ReportSession,
                override val metadata: FinalizedMetadata,
                override val ioJob: Job
            ) : Teardown, WithJob

            // match end state, failed final flush to disk, allow manual flush
            data class FinalizingFailed(
                override val session: ReportSession,
                override val metadata: FinalizedMetadata
            ) : Teardown
        }
    }

    // core state
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reportState: ReportState = Closed

    // helper functions for guard conditions
    private val acceptsPayloads get() = reportState is Setup || reportState is Running
    private val autoflushActive get() = reportState is Running

    /**
     * Helper function for directly calling from the MatchStart handler.
     */
    private fun transitionToInitializing(event: MatchEvent.MatchStart) {
        val match = event.result

        val session = ReportSession(
            startedAt = match.startedAt,
            runner = match.runner,
            hunters = match.hunters.toList(),
        )

        transitionToInitializing(session)
    }

    /**
     * Open database connection and create database if not existing already.
     * Destination of states: Closed, InitializingFailed
     * Source of states: Open, InitializingFailed, FinalizingPending
     */
    private fun transitionToInitializing(session: ReportSession, callback: ((ReportOpResult) -> Unit)? = null) {
        val ioJob = scope.launch(Dispatchers.IO) {
            val dbErr = runCatching { storage.openMatch(session) }.exceptionOrNull()

            // transition from initializing
            scheduler.runOnMainThread {
                val result = if (dbErr == null) {
                    ReportOpResult.Ok("Successfully opened database.")
                } else {
                    ReportOpResult.Err(ReportOpFailure.DB_FAILED_TO_OPEN, dbErr)
                }

                when (val current = reportState) {
                    is Setup.Initializing -> {
                        reportState = if (dbErr == null) {
                            Running.Open(current.session, startAutoflushJob(session.startedAt))
                        } else {
                            Setup.InitializingFailed(current.session)
                        }
                        callback?.invoke(result)
                    }

                    is Teardown.FinalizingPending -> {
                        transitionToFinalizing(current.session, current.metadata)
                        callback?.invoke(result)
                    }

                    // all other states mean nothing to this callback. shouldn't be in those states
                    else -> {
                        callback?.invoke(ReportOpResult.Err(ReportOpFailure.INVALID_STATE, dbErr))
                    }
                }
            }
        }

        // this shouldn't get clobbered by the coroutine launch since the callback happens at minimum on the next tick
        reportState = Setup.Initializing(session, ioJob)
    }


    /**
     * Helper function for directly calling from the MatchEnd handler.
     */
    private fun transitionToFinalizing(event: MatchEvent.MatchEnd) {
        val match = event.result
        val state = reportState

        if (state !is WithSession) return

        val session = state.session

        val metadata = FinalizedMetadata(
            endedAt = match.endedAt,
            outcome = match.outcome,
            durationTicks = session.latestTick
        )

        transitionToFinalizing(session, metadata)
    }

    /**
     * Write match results and remaining collected reports to disk.
     * Destination of states: InitializingFailed, Open, FinalizingFailed, FinalizingPending.
     * Source of states: Closed, FinalizingFailed
     * Handles DB creation (since InitializingFailed means DB was not created) as well.
     */
    private fun transitionToFinalizing(
        session: ReportSession,
        metadata: FinalizedMetadata,
        callback: ((ReportOpResult) -> Unit)? = null
    ) {
        val ioJob = scope.launch(Dispatchers.IO) {
            val (drainedSession, batch) = session.drain()

            /**
             * three write operations that can fail:
             *  (1) check DB exists and create if not
             *  (2) flush everything out of the session if there are still events
             *  (3) write finalized metadata to the DB
             * short circuit on the first error, since further checks require previous
             * to be healthy.
             */
            val pipelineResult = runCatching {
                // if coming from InitializingFailed, DB was never created. attempt to fix that.
                if (!storage.isOpen) storage.openMatch(session)
            }.mapCatching {
                storage.appendFlush(drainedSession.matchId, batch, Clock.System.now())
            }.mapCatching {
                storage.finalizeMatch(
                    drainedSession.matchId,
                    metadata.endedAt,
                    metadata.outcome,
                    metadata.durationTicks
                )
            }

            val structuralError = pipelineResult.exceptionOrNull()

            scheduler.runOnMainThread {
                val current = reportState

                // Pre-calculate the operation results
                val result = if (structuralError == null) {
                    ReportOpResult.Ok("Successfully finalized match report.")
                } else {
                    ReportOpResult.Err(ReportOpFailure.DB_FAILED_TO_FINALIZE, structuralError)
                }

                when (current) {
                    is Teardown.Finalizing -> {
                        reportState = if (structuralError == null) {
                            Closed
                        } else {
                            Teardown.FinalizingFailed(current.session.restoreBatch(batch), metadata)
                        }
                        callback?.invoke(result)
                    }

                    else -> {
                        // callback shouldn't be executing in other states than Finalizing. maybe someone called discard()?
                        callback?.invoke(ReportOpResult.Err(ReportOpFailure.INVALID_STATE, structuralError))
                    }
                }
            }
        }

        reportState = Teardown.Finalizing(session, metadata, ioJob)
    }

    /**
     * Start coroutine to autoflush reported events, interval anchored on match start instant.
     * Flush operations triggered by this job log to console on failure.
     * Interval is based on wall clock time, adjustable by minutes
     * in [PhConfig.flushMinutesInterval].
     */
    private fun startAutoflushJob(startedAt: Instant): Job? {
        val cachedFlushInterval = config.flushMinutesInterval
        if (cachedFlushInterval == Duration.ZERO) return null

        fun flushOnMain() {
            // no-op if somehow the coroutine is active when it shouldn't be
            if (!autoflushActive) return

            // skip flush if flush is happening during I/O (Running.Writing)
            if (reportState !is Running.Open) {
                logger.warn("Autoflush operation skipped; I/O already in progress")
                return
            }

            flush({ opResult ->
                when (opResult) {
                    is ReportOpResult.Ok -> {} // swallow successful flushes
                    is ReportOpResult.Err -> logger.error(
                        "Failed to perform autoflush operation",
                        opResult.cause
                    )
                }
            })
        }

        val job = scope.launch {
            while (isActive) {
                val elapsed = Clock.System.now() - startedAt
                val intervalNs = cachedFlushInterval.inWholeNanoseconds
                val elapsedNs = elapsed.inWholeNanoseconds
                val nextTick = (intervalNs - (elapsedNs % intervalNs)).nanoseconds
                delay(nextTick)

                scheduler.runOnMainThread(::flushOnMain)
            }
        }

        return job
    }

    /**
     * Helper function to hide the giant when brace for appending a new event
     * since copy doesn't work on interfaces.
     */
    private fun ReportState.appendEvent(newEvent: ReportableEvent): ReportState {
        if (this !is WithSession) return this

        val newSession = this.session.withEvent(newEvent)

        return when (this) {
            is Setup.Initializing -> copy(session = newSession)
            is Setup.InitializingFailed -> copy(session = newSession)
            is Running.Open -> copy(session = newSession)
            is Running.Writing -> copy(session = newSession)

            // everything below should not be possible
            is Teardown.FinalizingPending -> copy(session = newSession)
            is Teardown.Finalizing -> copy(session = newSession)
            is Teardown.FinalizingFailed -> copy(session = newSession)

            else -> this // smart-cast doesn't realize that Closed was ruled out so need to else (or `is Closed`)
        }
    }

    override val blockReason: ReportSessionBlockReason?
        get() = when (reportState) {
            Closed -> null
            is Teardown -> ReportSessionBlockReason.FINALIZE_PENDING
            else -> ReportSessionBlockReason.SESSION_ALREADY_ACTIVE
        }

    /**
     * Flush operation drains
     */
    override fun flush(callback: (ReportOpResult) -> Unit) {
        // callback handles how feedback is done
        // todo: implement flush interval task that calls this w/ operator and logger feedback
        // todo: implement manual flush task that calls this w/ sender and logger feedback
        // todo: implement match end flush task that calls this w/ operator and logger feedback

        /* todo: manual flush has these branches:
        *       (1) if reportState is Closed, report error: no data to flush
        *       (2) if reportState is InitializedFailed: attempt to create DB then flush stored data
        *       (3) if reportState is FinalizeFailed: re-attempt finalized flush to DB
        *       (4) if reportState is Active: attempt to flush
        *       (5) if reportState is either Initializing or Finalizing: block writes; report is doing DB work
        *
        *   todo: all flushable manual branches (2, 3, 4) have two branches:
        *       (a) if DB I/O already in progress: reject and notify to try again
        *       (b) if DB I/O not in progress: (do extra DB ops if needed and) flush current data to DB
        *
        *   todo: DB I/O is detected by checking if the TaskHandle is in use by some task
        *
        *   todo: Autoflush behavior notes:
        *       - Autoflush is forced-disabled after reportState is InitializedFailed and naturally disabled by Finalizing.
        *         By virtue of order, that means that it will not be active in Closed or FinalizeFailed.
        *       - Autoflush is re-enabled (at the same anchor: match start, not at re-enable time) when transitioning from
        *         InitializedFailed -> Active and from Initializing -> Active
        *       - Autoflush will be skipped if the interval hits while it is already flushing data to DB
        *
        *   todo: match end finalization call MUST NOT be lost. if DB I/O in progress, it must be able to finalize
        *    the report. so it should immediately transition Active->Finalizing and wait until the DB is free
        *    without blocking the main thread.
        */

        /**
         *  Autoflush no-ops itself if it happens on any state but [Running.Open], so we should be okay with
         *  assuming that this flush() is called manually by an operator.
         */
        when (val state = reportState) {
            is Closed -> callback(ReportOpResult.Err(ReportOpFailure.NO_OPEN_SESSION))
            // rejecting all cases with I/O just to happens to nicely account for all unspecified sealed cases :D
            is WithJob -> callback(ReportOpResult.Err(ReportOpFailure.IO_IN_PROGRESS))
            // happy path #1 - standard flush operation
            is Running.Open -> {
                if (state.session.empty) {
                    callback(ReportOpResult.Err(ReportOpFailure.NOTHING_TO_FLUSH))
                    return
                }

                val (drainedSession, batch) = state.session.drain()
                reportState = state.copy(session = drainedSession)

                scope.launch {
                    val flushError = runCatching {
                        storage.appendFlush(drainedSession.matchId, batch, Clock.System.now())
                    }.exceptionOrNull()

                    scheduler.runOnMainThread {
                        if (flushError != null) {
                            callback(ReportOpResult.Err(ReportOpFailure.WRITE_FAILED, flushError))
                        } else {
                            val totalRecords = batch.projectiles.size + batch.snapshots.size + batch.events.size
                            callback(ReportOpResult.Ok("Successfully flushed $totalRecords records to disk."))
                        }
                    }
                }
            }
            // (somewhat) happy path #2 - failed DB open operation
            is Setup.InitializingFailed -> transitionToInitializing(state.session)
            // (somewhat) happy path #3 - failed report finalization operation
            is Teardown.FinalizingFailed -> transitionToFinalizing(state.session, state.metadata)
        }
    }

    /**
     * Teardown all functionality immediately. Must teardown I/O and autoflush.
     * Called by `/ph report discard` at any point in the match lifecycle.
     */
    override fun discard(): ReportOpResult {
        val state = reportState
        if (state is Closed) return ReportOpResult.Err(ReportOpFailure.NO_OPEN_SESSION)

        if (state is Running) state.autoflushJob?.cancel()
        if (state is WithJob) state.ioJob.cancel()
        reportState = Closed
        return ReportOpResult.Ok("Discarded reporting session. Match must be (re)started to resume reporting.")
    }

    /**
     * Graceful shutdown for plugin disable.
     */
    fun shutdown() {
        val state = reportState
        if (state is Running) state.autoflushJob?.cancel()
        if (state is WithJob) {
            val result = runBlocking {
                withTimeoutOrNull(1.minutes) {
                    state.ioJob.join()
                    true
                }
            }

            if (result == null) {
                logger.warn("Report operation failed to save to disk: write timed out before shutdown")
            } else {
                logger.info("Successfully settled remaining report operations to disk.")
            }
        }

        reportState = Closed
    }

    /**
     * Listen for domain events, namely MatchStart and MatchEnd
     */
    fun onMatchEvent(event: MatchEvent) {
        when (event) {
            is MatchEvent.MatchStart -> openSession(event)
            is MatchEvent.MatchEnd -> closeSession(event)
            else -> {}
        }
    }

    /**
     * Listen for gameplay events for final report.
     * Only records events when session accepts events (guard on [acceptsPayloads])
     */
    fun onReportableEvent(event: ReportableEvent) {
        if (!acceptsPayloads) return // reject events on Teardown phase & Closed
        val newState = reportState.appendEvent(event)
        reportState = newState
    }

    /**
     * Nicely-named function to parallel with closeSession()
     */
    private fun openSession(event: MatchEvent.MatchStart) = transitionToInitializing(event)

    /**
     * Trigger for transition to [Teardown] states upon [MatchEvent.MatchEnd]
     * signal from core.
     * Handles destinations of: Initializing, InitializingFailed, Open, Writing
     * Destinations: FinalizingPending (for I/O states), Finalizing
     */
    private fun closeSession(event: MatchEvent.MatchEnd) {
        when (val state = reportState) {
            Closed -> {}
            is Setup.InitializingFailed, is Running.Open -> transitionToFinalizing(event)
            is Setup.Initializing, is Running.Writing -> {
                val match = event.result
                val session = state.session

                val metadata = FinalizedMetadata(
                    endedAt = match.endedAt,
                    outcome = match.outcome,
                    durationTicks = session.latestTick
                )

                reportState = Teardown.FinalizingPending(
                    session = state.session,
                    metadata = metadata,
                    ioJob = state.ioJob
                )
            }

            else -> {}
        }
    }
}