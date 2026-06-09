package io.github.ganyuke.peoplehunt.core.services.core

import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.events.MatchEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportableEvent
import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.core.events.models.MatchPlayer
import io.github.ganyuke.peoplehunt.core.ports.inbound.MatchPort
import io.github.ganyuke.peoplehunt.core.ports.outbound.SchedulerPort
import io.github.ganyuke.peoplehunt.core.ports.outbound.TaskHandle
import io.github.ganyuke.peoplehunt.core.services.core.models.MatchFailureReason
import io.github.ganyuke.peoplehunt.core.services.core.models.MatchOutcome
import io.github.ganyuke.peoplehunt.core.services.core.models.MatchResult
import io.github.ganyuke.peoplehunt.core.services.core.models.MatchState
import io.github.ganyuke.peoplehunt.core.utils.*
import kotlin.time.Clock

class MatchEngine(
    private val scheduler: SchedulerPort,
    private val outbound: MatchEventBus,
    private val config: PhConfig
) : MatchPort {
    private val matchIntervalService: MatchIntervalService = MatchIntervalService(config, scheduler, outbound)
    private val tasks = mutableListOf<TaskHandle>()

    // need to store this to support `/ph status last` feature
    override var lastMatchResult: MatchState.Finished? = null
        private set

    override var currentStatus: MatchState = MatchState.Idle(null, emptySet())
        private set

    // put match in state where runner movement triggers start with `/ph prime`
    override fun prime(onlinePlayers: List<MatchPlayer>): MatchResult = when (val currentState = currentStatus) {
        is MatchState.Primed -> MatchResult.Err(MatchFailureReason.ALREADY_PRIMED)
        is MatchState.Active -> MatchResult.Err(MatchFailureReason.ALREADY_STARTED)
        is MatchState.Idle, is MatchState.Finished -> {
            val runnerCandidate = currentState.runner
            if (runnerCandidate == null) {
                MatchResult.Err(MatchFailureReason.NO_RUNNER_SPECIFIED)
            } else {
                val huntersToUse = currentState.hunters.ifEmpty {
                    // if empty, just use all the online players
                    onlinePlayers.filter { it isNotReally runnerCandidate }
                }.toSet()
                currentStatus = MatchState.Primed(runnerCandidate, huntersToUse, Clock.System.now())
                MatchResult.Ok("Match primed")
            }
        }
    }

    // manhunt lifecycle events triggered by game events
    fun onEvent(event: ReportableEvent) {
        when (val currentState = currentStatus) {
            // start match when runner moves and match was primed
            is MatchState.Primed -> {
                if (event.payload is ReportablePayload.PlayerMovedByBlock && event.payload.player isReally currentState.runner)
                    startMatch(currentState.runner, currentState.hunters)
            }

            is MatchState.Active -> {
                when (event.payload) {
                    is ReportablePayload.PlayerDied -> {
                        if (event.payload.player isReally currentState.runner) {
                            endMatch(currentState, MatchOutcome.HUNTER_VICTORY)
                        }
                    }

                    is ReportablePayload.EntityDied -> {
                        if (event.payload.entityIdentifier == "minecraft:ender_dragon") {
                            endMatch(currentState, MatchOutcome.RUNNER_VICTORY)
                        }
                    }

                    else -> {}
                }
            }

            else -> {}
        }
    }

    // called from `/ph start`, force match to immediately start
    override fun forceStart(onlinePlayers: List<MatchPlayer>): MatchResult =
        when (val currentState = currentStatus) {
            is MatchState.Active -> MatchResult.Err(MatchFailureReason.ALREADY_STARTED)
            is MatchState.Primed -> {
                startMatch(currentState.runner, currentState.hunters)
                MatchResult.Ok()
            }

            is MatchState.Idle, is MatchState.Finished -> {
                val runnerToUse = currentState.runner
                if (runnerToUse != null) {
                    val huntersToUse = currentState.hunters.ifEmpty {
                        onlinePlayers.filter { it isNotReally runnerToUse }
                    }.toSet()
                    startMatch(runnerToUse, huntersToUse)
                    MatchResult.Ok()
                } else {
                    MatchResult.Err(MatchFailureReason.NO_RUNNER_SPECIFIED)
                }
            }
        }

    // called from `/ph stop`, force match to immediately stop
    override fun forceEnd(): MatchResult =
        when (val currentState = currentStatus) {
            // prime doesn't do anything put change match status, so just swap it back
            is MatchState.Primed -> {
                currentStatus = MatchState.Idle(currentState.runner, currentState.hunters)
                MatchResult.Ok("Match prime released")
            }
            // need to formally stop the match if the match already started
            is MatchState.Active -> {
                endMatch(currentState, MatchOutcome.INCONCLUSIVE)
                MatchResult.Ok("Match force-stopped")
            }

            // IDLE and ENDED statuses are well... not started
            else -> MatchResult.Err(MatchFailureReason.NOT_RUNNING)
        }

    private fun startMatch(activeRunner: MatchPlayer, activeHunters: Set<MatchPlayer>) {
        val startTime = Clock.System.now()
        val status = MatchState.Active(activeRunner, activeHunters, startTime)
        currentStatus = status

        outbound.post(MatchEvent.MatchStart(status))
        outbound.post(MatchEvent.BroadcastNotification("Match started"))

        // maybe add a delay later
        outbound.post(MatchEvent.GiveHuntersCompass(activeHunters.map { it.uuid }.toSet()))

        tasks += scheduler.everyTicks(config.compassTickInterval) {
            outbound.post(MatchEvent.CompassTick)
        }

        matchIntervalService.start { startTime }?.let { tasks += it }
    }

    private fun endMatch(activeState: MatchState.Active, outcome: MatchOutcome) {
        tasks.forEach { it.cancel() }
        tasks.clear()

        val endTime = Clock.System.now()
        val finishedStatus = MatchState.Finished(
            runner = activeState.runner,
            hunters = activeState.hunters,
            startedAt = activeState.startedAt,
            endedAt = endTime,
            outcome = outcome
        )

        // update state machine to finished
        // and also write to the historical state variable
        currentStatus = finishedStatus
        this.lastMatchResult = finishedStatus

        val elapsed = (endTime - activeState.startedAt).inWholeSeconds
        outbound.post(MatchEvent.BroadcastNotification("Match ended after ${formatElapsed(elapsed)}"))

        // schedule so the status message appears immediately
        // after the player death notification instead of before
        scheduler.after(1L) {
            outbound.post(MatchEvent.MatchEnd(finishedStatus))
        }
    }

    fun reset() {
        currentStatus = MatchState.Idle(currentStatus.runner, currentStatus.hunters)
    }

    fun fullReset() {
        tasks.forEach { it.cancel() }
        tasks.clear()
        currentStatus = MatchState.Idle(null, emptySet())
    }

    fun shutdown() {
        if (currentStatus is MatchState.Active) {
            endMatch(currentStatus as MatchState.Active, MatchOutcome.INCONCLUSIVE)
        }
        fullReset()
        matchIntervalService.shutdown()
    }

    // prevent mutation while game is already active
    private fun guardMutation(block: (MatchState.Idle) -> MatchResult): MatchResult {
        return when (val currentState = currentStatus) {
            is MatchState.Primed -> MatchResult.Err(MatchFailureReason.ALREADY_PRIMED)
            is MatchState.Active -> MatchResult.Err(MatchFailureReason.ALREADY_STARTED)
            is MatchState.Finished -> block(MatchState.Idle(currentState.runner, currentState.hunters))
            is MatchState.Idle -> block(currentState)
        }
    }

    override fun setRunner(player: MatchPlayer) = guardMutation { idleState ->
        // success: overriding existing runner / no runner
        // failure: candidate already runner / hunter
        if (idleState.runner isReally player) return@guardMutation MatchResult.Err(MatchFailureReason.PLAYER_ALREADY_RUNNER)
        if (idleState.hunters reallyContains player) return@guardMutation MatchResult.Err(MatchFailureReason.PLAYER_ALREADY_HUNTER)

        currentStatus = idleState.copy(runner = player)
        MatchResult.Ok("Set ${player.name} as the runner")
    }

    override fun removeRunner(player: MatchPlayer) = guardMutation { idleState ->
        // success: removing current runner
        // failure: candidate not runner
        if (idleState.runner isNotReally player) return@guardMutation MatchResult.Err(MatchFailureReason.PLAYER_NOT_IN_GROUP)

        currentStatus = idleState.copy(runner = null)
        MatchResult.Ok("Removed ${player.name} as the runner")
    }

    override fun clearRunner(): MatchResult = guardMutation { idleState ->
        // unconditionally clear runner
        currentStatus = idleState.copy(runner = null)
        MatchResult.Ok("Cleared the runner")
    }

    override fun addHunter(player: MatchPlayer) = guardMutation { idleState ->
        // success: adding candidate hunter
        // failure: candidate already hunter
        if (idleState.runner isReally player) return@guardMutation MatchResult.Err(MatchFailureReason.PLAYER_ALREADY_HUNTER)

        currentStatus = idleState.copy(hunters = idleState.hunters + player)
        MatchResult.Ok("Added ${player.name} as a hunter")
    }

    override fun removeHunter(player: MatchPlayer): MatchResult = guardMutation { idleState ->
        // success: removing candidate hunter
        // failure: candidate not a hunter
        if (!(idleState.hunters reallyContains player)) return@guardMutation MatchResult.Err(MatchFailureReason.PLAYER_NOT_IN_GROUP)

        currentStatus = idleState.copy(hunters = idleState.hunters - player)
        MatchResult.Ok("Removed ${player.name} as a hunter")
    }

    override fun clearHunters() = guardMutation { idleState ->
        // unconditionally clear hunters
        val originalSize = idleState.hunters.size
        currentStatus = idleState.copy(hunters = emptySet())
        MatchResult.Ok("Cleared $originalSize hunters")
    }
}
