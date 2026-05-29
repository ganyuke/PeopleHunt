package io.github.ganyuke.peoplehunt.core.services

import kotlin.uuid.Uuid
import io.github.ganyuke.peoplehunt.core.Utils.formatElapsed
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
) {
    enum class MatchPhase { IDLE, PRIMED, ACTIVE, FINISHED }
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

    private val matchId: Uuid = Uuid.random()
    private var matchPhase: MatchPhase = MatchPhase.IDLE

    private var matchStartTime: Instant? = null
    private var matchPrimedTime: Instant? = null
    private var matchEndTime: Instant? = null

    private var matchOutcome: MatchOutcome = MatchOutcome.INCONCLUSIVE

    private val tasks = mutableListOf<TaskHandle>()

    private var runner: MatchPlayer? = null
    private var hunters: Set<MatchPlayer> = emptySet()

    fun getMatchId(): Uuid = matchId

    fun getMatchStatus() : MatchStatus {
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

            MatchPhase.FINISHED -> MatchStatus.Finished(
                checkNotNull(runner) { "Runner must be set when match is finished" },
                hunterList,
                checkNotNull(matchStartTime) { "Start time must be set when match is finished" },
                checkNotNull(matchEndTime) { "End time must be set when match is finished" },
                matchOutcome
            )
        }
    }

    // put match in state where runner movement triggers start with `/ph prime`
    fun prime(): MatchResult = when (matchPhase) {
        MatchPhase.PRIMED -> MatchResult.Err(FailureReason.ALREADY_PRIMED)
        MatchPhase.ACTIVE -> MatchResult.Err(FailureReason.ALREADY_STARTED)
        MatchPhase.IDLE, MatchPhase.FINISHED -> {
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
                if (event is ReportableEvent.PlayerMoved && event.player == checkNotNull(runner).uuid)
                    startMatch()
            }

            MatchPhase.ACTIVE -> {
                if (event is ReportableEvent.EntityDied) when {
                    event.player == checkNotNull(runner) { "Runner must be set when match is active" }
                            .uuid -> // match active with null runner should not happen
                        endMatch(MatchOutcome.HUNTER_VICTORY)

                    event.entityIdentifier == "minecraft:ender_dragon" ->
                        endMatch(MatchOutcome.RUNNER_VICTORY)
                }
            }

            else -> {}
        }
    }

    // called from `/ph start`, force match to immediately start
    fun forceStart(): MatchResult = when (matchPhase) {
        MatchPhase.ACTIVE -> MatchResult.Err(FailureReason.ALREADY_STARTED)
        MatchPhase.PRIMED, MatchPhase.IDLE, MatchPhase.FINISHED -> {
            if (this.runner != null) {
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
        outbound.post(MatchEvent.GiveHuntersCompass(hunters.map{ it.uuid }.toSet()))

        tasks += scheduler.everyTicks(4L) {
            outbound.post(MatchEvent.CompassTick)
        }
        tasks += scheduler.everyTicks(20L * 60 * 30) {
            outbound.post(MatchEvent.IntervalElapsed(elapsedSeconds()))
        }
    }

    private fun endMatch(reason: MatchOutcome) {
        matchOutcome = reason
        matchEndTime = Clock.System.now()
        matchPhase = MatchPhase.FINISHED
        tasks.forEach { it.cancel() }
        tasks.clear()
        outbound.post(
            MatchEvent.BroadcastNotification(
                "Match ended after ${formatElapsed(elapsedSeconds())}"
            )
        )
        outbound.post(MatchEvent.MatchEnd(reason))
    }

    private fun elapsedSeconds() = (Clock.System.now() - checkNotNull(matchStartTime)).inWholeSeconds

    fun getRunner(): MatchPlayer? = runner
    fun getHunters(): Set<MatchPlayer> = hunters

    fun setRunner(player: MatchPlayer): MatchResult {
        if (hunters.contains(player)) return MatchResult.Err(FailureReason.PLAYER_ALREADY_RUNNER)
        this.runner = player
        return MatchResult.Ok("Set ${player.name} as the runner")
    }

    fun removeRunner(player: MatchPlayer): MatchResult {
        if (this.runner != player) return MatchResult.Err(FailureReason.PLAYER_NOT_IN_GROUP)
        this.runner = null
        return MatchResult.Ok("Removed ${player.name} as the runner")
    }

    fun clearRunner(): MatchResult {
        this.runner = null
        return MatchResult.Ok("Cleared the runner")
    }

    fun addHunter(player: MatchPlayer): MatchResult {
        if (this.runner == player) return MatchResult.Err(FailureReason.PLAYER_ALREADY_HUNTER)
        this.hunters += player
        return MatchResult.Ok("Added ${player.name} as a hunter")
    }

    fun removeHunter(player: MatchPlayer): MatchResult {
        if (!this.hunters.contains(player)) return MatchResult.Err(FailureReason.PLAYER_NOT_IN_GROUP)
        this.hunters -= player
        return MatchResult.Ok("Removed ${player.name} as a hunter")
    }

    fun clearHunters(): MatchResult {
        val originalSize = this.hunters.size
        this.hunters = emptySet()
        return MatchResult.Ok("Cleared $originalSize hunters")
    }
}
