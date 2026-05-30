package io.github.ganyuke.peoplehunt.core.services.core

import io.github.ganyuke.peoplehunt.core.Utils
import kotlin.uuid.Uuid
import io.github.ganyuke.peoplehunt.core.Utils.formatElapsed
import io.github.ganyuke.peoplehunt.core.Utils.isNotReally
import io.github.ganyuke.peoplehunt.core.Utils.isReally
import io.github.ganyuke.peoplehunt.core.Utils.reallyContains
import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.events.MatchEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportableEvent
import io.github.ganyuke.peoplehunt.core.ports.SchedulerPort
import io.github.ganyuke.peoplehunt.core.ports.TaskHandle
import kotlin.time.Clock
import kotlin.time.Instant

class MatchEngine(
    private val scheduler: SchedulerPort,
    private val outbound: MatchEventBus,
    private val config: Utils.PhConfig
) {
    private enum class MatchPhase { IDLE, PRIMED, ACTIVE, FINISHED }
    enum class MatchOutcome { RUNNER_VICTORY, HUNTER_VICTORY, INCONCLUSIVE }

    data class MatchPlayer(val uuid: Uuid, val name: String)

    sealed interface MatchStatus {
        data class Idle(
            val runner: MatchPlayer?,
            val hunters: List<MatchPlayer>,
        ) : MatchStatus

        data class Primed(
            val runner: MatchPlayer,
            val hunters: List<MatchPlayer>,
            val primedAt: Instant,
        ) : MatchStatus

        data class Active(
            val runner: MatchPlayer,
            val hunters: List<MatchPlayer>,
            val startedAt: Instant,
        ) : MatchStatus

        data class Finished(
            val runner: MatchPlayer,
            val hunters: List<MatchPlayer>,
            val startedAt: Instant,
            val endedAt: Instant,
            val outcome: MatchOutcome,
        ) : MatchStatus
    }

    sealed interface MatchResult {
        data class Ok(val message: String? = null) : MatchResult
        data class Err(val reason: FailureReason) : MatchResult
    }

    enum class FailureReason {
        ALREADY_PRIMED,
        ALREADY_STARTED,
        NOT_RUNNING,
        NO_RUNNER_SPECIFIED,
        PLAYER_ALREADY_RUNNER,
        PLAYER_ALREADY_HUNTER,
        PLAYER_NOT_IN_GROUP,
    }

    val matchId: Uuid = Uuid.random()

    private var matchPhase: MatchPhase = MatchPhase.IDLE
    private val matchIntervalService: MatchIntervalService = MatchIntervalService(config, scheduler, outbound)
    private val tasks = mutableListOf<TaskHandle>()

    private var matchStartTime: Instant? = null
    private var matchPrimedTime: Instant? = null

    var runner: MatchPlayer? = null
        private set

    var hunters: Set<MatchPlayer> = emptySet()
        private set

    var lastMatchResult: MatchStatus.Finished? = null
        private set

    val currentStatus: MatchStatus
        get() {
            val hunterList = hunters.toList()
            return when (matchPhase) {
                MatchPhase.IDLE -> MatchStatus.Idle(runner, hunterList)
                MatchPhase.PRIMED -> MatchStatus.Primed(
                    checkNotNull(runner) { "Runner must be set when match is primed" },
                    hunterList,
                    checkNotNull(matchPrimedTime) { "Primed time must be set when match is primed" }
                )

                MatchPhase.ACTIVE -> MatchStatus.Active(
                    checkNotNull(runner) { "Runner must be set when match is active" },
                    hunterList,
                    checkNotNull(matchStartTime) { "Start time must be set when match is active" }
                )

                MatchPhase.FINISHED -> checkNotNull(lastMatchResult) { "Last match result was not written on match completion!" }
            }
        }

    // put match in state where runner movement triggers start with `/ph prime`
    fun prime(onlinePlayers: List<MatchPlayer>): MatchResult = when (matchPhase) {
        MatchPhase.PRIMED -> MatchResult.Err(FailureReason.ALREADY_PRIMED)
        MatchPhase.ACTIVE -> MatchResult.Err(FailureReason.ALREADY_STARTED)
        MatchPhase.IDLE, MatchPhase.FINISHED -> {
            if (hunters.isEmpty()) {
                hunters = onlinePlayers.filter { it isNotReally runner }.toSet()
            }

            val currentRunner = runner
            if (currentRunner != null) {
                matchPrimedTime = Clock.System.now()
                matchPhase = MatchPhase.PRIMED
                MatchResult.Ok("Match primed")
            } else {
                MatchResult.Err(FailureReason.NO_RUNNER_SPECIFIED)
            }
        }
    }

    // manhunt lifecycle events triggered by game events
    fun onEvent(event: ReportableEvent) {
        when (matchPhase) {
            // start match when runner moves and match was primed
            MatchPhase.PRIMED -> {
                if (event is ReportableEvent.PlayerMoved && event.player isReally checkNotNull(runner))
                    startMatch()
            }

            MatchPhase.ACTIVE -> {
                if (event is ReportableEvent.EntityDied) when {
                    event.player isReally checkNotNull(runner) { "Runner must be set when match is active" }
                        -> // match active with null runner should not happen
                        endMatch(MatchOutcome.HUNTER_VICTORY)

                    event.entityIdentifier == "minecraft:ender_dragon" ->
                        endMatch(MatchOutcome.RUNNER_VICTORY)
                }
            }

            else -> {}
        }
    }

    // called from `/ph start`, force match to immediately start
    fun forceStart(onlinePlayers: List<MatchPlayer>): MatchResult = when (matchPhase) {
        MatchPhase.ACTIVE -> MatchResult.Err(FailureReason.ALREADY_STARTED)
        MatchPhase.PRIMED, MatchPhase.IDLE, MatchPhase.FINISHED -> {
            if (this.runner != null) {
                if (hunters.isEmpty()) {
                    hunters = onlinePlayers.filter { it isNotReally runner }.toSet()
                }

                startMatch()
                MatchResult.Ok()
            } else {
                MatchResult.Err(FailureReason.NO_RUNNER_SPECIFIED)
            }
        }
    }

    // called from `/ph stop`, force match to immediately stop
    fun forceEnd(): MatchResult = when (matchPhase) {
        // prime doesn't do anything put change match status, so just swap it back
        MatchPhase.PRIMED -> {
            matchPrimedTime = null
            matchPhase = MatchPhase.IDLE
            MatchResult.Ok("Match prime released")
        }

        // need to formally stop the match if the match already started
        MatchPhase.ACTIVE -> {
            endMatch(MatchOutcome.INCONCLUSIVE)
            MatchResult.Ok("Match force-stopped")
        }

        // IDLE and ENDED statuses are well... not started
        else -> MatchResult.Err(FailureReason.NOT_RUNNING)
    }

    private fun startMatch() {
        matchPhase = MatchPhase.ACTIVE
        matchStartTime = Clock.System.now()
        val currentRunner = checkNotNull(runner)  // match is primed already so this shouldn't break
        outbound.post(MatchEvent.MatchStart(currentRunner, hunters))
        outbound.post(MatchEvent.BroadcastNotification("Match started"))

        // maybe add a delay later
        outbound.post(MatchEvent.GiveHuntersCompass(hunters.map { it.uuid }.toSet()))

        tasks += scheduler.everyTicks(config.compassTickInterval) {
            outbound.post(MatchEvent.CompassTick)
        }

        matchIntervalService.start { checkNotNull(matchStartTime) }?.let { tasks += it }
    }

    private fun endMatch(reason: MatchOutcome) {
        matchPhase = MatchPhase.FINISHED
        tasks.forEach { it.cancel() }
        tasks.clear()

        val matchEndTime = Clock.System.now()
        val hunterList = hunters.toList()

        val lastMatchResult = MatchStatus.Finished(
            checkNotNull(runner) { "Runner must be set when match is finished" },
            hunterList,
            checkNotNull(matchStartTime) { "Start time must be set when match is finished" },
            matchEndTime,
            reason
        )

        // i guess in concurrent contexts, lastMatchResult could become null mid-code
        // so we need to use the local lastMatchResult here
        this.lastMatchResult = lastMatchResult

        val matchEndMessage = "Match ended after ${formatElapsed(elapsedSeconds())}"
        outbound.post(MatchEvent.BroadcastNotification(matchEndMessage))

        // schedule so the status message appears immediately
        // after the player death notification instead of before
        scheduler.after(1L, {
            outbound.post(MatchEvent.MatchEnd(lastMatchResult))
        })
    }

    fun reset() {
        matchPhase = MatchPhase.IDLE
        matchStartTime = null
        matchPrimedTime = null
    }

    fun fullReset() {
        reset()
        tasks.forEach { it.cancel() }
        tasks.clear()
        runner = null
        hunters = emptySet()
    }

    fun shutdown() {
        fullReset()
        matchIntervalService.shutdown()
    }

    private fun elapsedSeconds() = (Clock.System.now() - checkNotNull(matchStartTime)).inWholeSeconds

    // prevent mutation while game is already active
    private fun guardMutation(block: () -> MatchResult): MatchResult = when (matchPhase) {
        MatchPhase.PRIMED -> MatchResult.Err(FailureReason.ALREADY_PRIMED)
        MatchPhase.ACTIVE -> MatchResult.Err(FailureReason.ALREADY_STARTED)
        else -> {
            if (matchPhase == MatchPhase.FINISHED) reset() // clear /ph status on mutation
            block()
        }
    }

    fun setRunner(player: MatchPlayer) = guardMutation {
        // success: overriding existing runner / no runner
        // failure: candidate already runner / hunter
        if (this.runner isReally player) return@guardMutation MatchResult.Err(FailureReason.PLAYER_ALREADY_RUNNER)
        if (hunters reallyContains player) return@guardMutation MatchResult.Err(FailureReason.PLAYER_ALREADY_HUNTER)

        this.runner = player
        MatchResult.Ok("Set ${player.name} as the runner")
    }

    fun removeRunner(player: MatchPlayer) = guardMutation {
        // success: removing current runner
        // failure: candidate not runner
        if (this.runner isNotReally player) return@guardMutation MatchResult.Err(FailureReason.PLAYER_NOT_IN_GROUP)

        this.runner = null
        MatchResult.Ok("Removed ${player.name} as the runner")
    }

    fun clearRunner(): MatchResult = guardMutation {
        // unconditionally clear runner
        this.runner = null
        MatchResult.Ok("Cleared the runner")
    }

    fun addHunter(player: MatchPlayer) = guardMutation {
        // success: adding candidate hunter
        // failure: candidate already hunter
        if (this.runner isReally player) return@guardMutation MatchResult.Err(FailureReason.PLAYER_ALREADY_HUNTER)

        this.hunters += player
        MatchResult.Ok("Added ${player.name} as a hunter")
    }

    fun removeHunter(player: MatchPlayer): MatchResult = guardMutation {
        // success: removing candidate hunter
        // failure: candidate not a hunter
        if (!(this.hunters reallyContains player)) return@guardMutation MatchResult.Err(FailureReason.PLAYER_NOT_IN_GROUP)

        this.hunters -= player
        MatchResult.Ok("Removed ${player.name} as a hunter")
    }

    fun clearHunters() = guardMutation {
        // unconditionally clear hunters
        val originalSize = hunters.size
        hunters = emptySet()
        MatchResult.Ok("Cleared $originalSize hunters")
    }
}
