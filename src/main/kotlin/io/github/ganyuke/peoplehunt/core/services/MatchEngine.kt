package io.github.ganyuke.peoplehunt.core.services

import kotlin.uuid.Uuid
import io.github.ganyuke.peoplehunt.core.Utils.formatElapsed
import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.events.MatchEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportableEvent
import io.github.ganyuke.peoplehunt.core.ports.SchedulerPort
import io.github.ganyuke.peoplehunt.core.ports.TaskHandle

class MatchEngine(
    private val scheduler: SchedulerPort,
    private val outbound: MatchEventBus,
) {
    enum class MatchStatus { IDLE, PRIMED, STARTED, ENDED }
    enum class MatchEndReason { RUNNER_VICTORY, HUNTER_VICTORY, INCONCLUSIVE }

    data class MatchPlayer(val uuid: Uuid, val name: String)

    sealed interface MatchResult {
        object Ok : MatchResult
        data class Err(val reason: FailureReason) : MatchResult
    }

    enum class FailureReason {
        ALREADY_PRIMED,
        ALREADY_STARTED,
        NOT_RUNNING,
        NO_RUNNER_SPECIFIED,
        PLAYER_ALREADY_RUNNER,
        PLAYER_ALREADY_HUNTER,
        PLAYER_NOT_IN_GROUP
    }

    private val matchId: Uuid = Uuid.random()
    private var matchStatus: MatchStatus = MatchStatus.IDLE
    private var matchStartTime: Long = 0L
    private val tasks = mutableListOf<TaskHandle>()

    private var runner: MatchPlayer? = null
    private var hunters: Set<MatchPlayer> = emptySet()

    fun getMatchId(): Uuid = matchId

    // put match in state where runner movement triggers start with `/ph prime`
    fun prime(): MatchResult = when (matchStatus) {
        MatchStatus.PRIMED -> MatchResult.Err(FailureReason.ALREADY_PRIMED)
        MatchStatus.STARTED -> MatchResult.Err(FailureReason.ALREADY_STARTED)
        MatchStatus.IDLE, MatchStatus.ENDED -> {
            val currentRunner = this.runner
            if (currentRunner != null) {
                this.matchStatus = MatchStatus.PRIMED
                outbound.post(MatchEvent.BroadcastNotification("Match primed. Runner: $currentRunner"))
                MatchResult.Ok
            } else {
                MatchResult.Err(FailureReason.NO_RUNNER_SPECIFIED)
            }
        }
    }

    // manhunt lifecycle events triggered by game events
    fun onEvent(event: ReportableEvent) {
        when (matchStatus) {
            // start match when runner moves and match was primed
            MatchStatus.PRIMED -> {
                if (event is ReportableEvent.PlayerMoved && event.player == runner)
                    startMatch()
            }

            MatchStatus.STARTED -> {
                if (event is ReportableEvent.EntityDied) when {
                    event.player == runner ->
                        endMatch(MatchEndReason.HUNTER_VICTORY)

                    event.entityIdentifier == "minecraft:ender_dragon" ->
                        endMatch(MatchEndReason.RUNNER_VICTORY)
                }
            }

            else -> {}
        }
    }

    // called from `/ph start`, force match to immediately start
    fun forceStart(): MatchResult = when (matchStatus) {
        MatchStatus.STARTED -> MatchResult.Err(FailureReason.ALREADY_STARTED)
        MatchStatus.PRIMED, MatchStatus.IDLE, MatchStatus.ENDED -> {
            if (this.runner != null) {
                startMatch()
                MatchResult.Ok
            } else {
                MatchResult.Err(FailureReason.NO_RUNNER_SPECIFIED)
            }
        }
    }

    // called from `/ph stop`, force match to immediately stop
    fun forceEnd(): MatchResult = when (matchStatus) {
        // prime doesn't do anything put change match status, so just swap it back
        MatchStatus.PRIMED -> {
            matchStatus = MatchStatus.IDLE
            outbound.post(MatchEvent.BroadcastNotification("Match prime released."))
            MatchResult.Ok
        }

        // need to formally stop the match if the match already started
        MatchStatus.STARTED -> {
            outbound.post(MatchEvent.BroadcastNotification("Match force-stopped."))
            endMatch(MatchEndReason.INCONCLUSIVE)
            MatchResult.Ok
        }

        // IDLE and ENDED statuses are well... not started
        else -> MatchResult.Err(FailureReason.NOT_RUNNING)
    }

    private fun startMatch() {
        matchStatus = MatchStatus.STARTED
        matchStartTime = System.currentTimeMillis()
        val currentRunner = requireNotNull(runner)  // match is primed already so this shouldn't break
        outbound.post(MatchEvent.MatchStart(currentRunner))
        outbound.post(MatchEvent.BroadcastNotification("Match started!"))

        tasks += scheduler.everyTicks(4L) {
            outbound.post(MatchEvent.CompassTick)
        }
        tasks += scheduler.everyTicks(20L * 60 * 30) {
            outbound.post(MatchEvent.IntervalElapsed(elapsedSeconds()))
        }
    }

    private fun endMatch(reason: MatchEndReason) {
        matchStatus = MatchStatus.ENDED
        tasks.forEach { it.cancel() }
        tasks.clear()
        outbound.post(MatchEvent.MatchEnd(reason))
        outbound.post(
            MatchEvent.BroadcastNotification(
                "Match ended: $reason after ${formatElapsed(elapsedSeconds())}"
            )
        )
    }

    private val clockMillis: () -> Long = System::currentTimeMillis

    private fun elapsedSeconds() = (clockMillis() - matchStartTime) / 1000

    fun getRunner(): MatchPlayer? = runner
    fun getHunters(): Set<MatchPlayer> = hunters

    fun setRunner(player: MatchPlayer): MatchResult {
        if (hunters.contains(player)) return MatchResult.Err(FailureReason.PLAYER_ALREADY_RUNNER)
        this.runner = player
        return MatchResult.Ok
    }

    fun removeRunner(player: MatchPlayer): MatchResult {
        if (this.runner != player) return MatchResult.Err(FailureReason.PLAYER_NOT_IN_GROUP)
        this.runner = null
        return MatchResult.Ok
    }

    fun clearRunner(): MatchResult {
        this.runner = null
        return MatchResult.Ok
    }

    fun addHunter(player: MatchPlayer): MatchResult {
        if (this.runner == player) return MatchResult.Err(FailureReason.PLAYER_ALREADY_HUNTER)
        this.hunters += player
        return MatchResult.Ok
    }

    fun removeHunter(player: MatchPlayer): MatchResult {
        if (!this.hunters.contains(player)) return MatchResult.Err(FailureReason.PLAYER_NOT_IN_GROUP)
        this.hunters -= player
        return MatchResult.Ok
    }

    fun clearHunters(): MatchResult {
        this.hunters = emptySet()
        return MatchResult.Ok
    }
}
