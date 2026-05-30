package io.github.ganyuke.peoplehunt.core.services.core

import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.events.MatchEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportableEvent
import io.github.ganyuke.peoplehunt.core.events.models.Pos4
import kotlin.uuid.Uuid

class CompassService(private val outbound: MatchEventBus) {
    private var runnerUuid: Uuid? = null
    private var runnerDim: Uuid? = null
    private var huntersUuid: Set<Uuid> = emptySet()
    private val runnerPosInDim: MutableMap<Uuid, Pos4> = mutableMapOf()

    fun onReportableEvent(event: ReportableEvent) : Unit =
        when (event) {
            // feature: update tracked position on runner movement in dimension
            is ReportableEvent.PlayerMoved -> {
                if (event.movementSnapshot.player.uuid != runnerUuid) return

                runnerDim = event.movementSnapshot.pos.w
                runnerPosInDim[event.movementSnapshot.pos.w] = event.movementSnapshot.pos
            }

            // feature: give compass on hunter respawn
            is ReportableEvent.PlayerRespawned -> {
                if (event.player.uuid !in huntersUuid) return
                outbound.post(MatchEvent.GiveHuntersCompass(setOf(event.player.uuid)))
            }

            else -> {}
        }

    fun onMatchEvent(event: MatchEvent) =
        when (event) {
            is MatchEvent.MatchStart -> {
                runnerUuid = event.runner.uuid
                huntersUuid = event.hunters.map { it.uuid }.toSet()
                runnerPosInDim.clear()
                runnerDim = null
            }

            is MatchEvent.CompassTick -> tick()
            is MatchEvent.MatchEnd -> {
                runnerUuid = null
                runnerDim = null
                runnerPosInDim.clear()
            }

            else -> {}
        }

    private fun tick() {
        val dim = runnerDim ?: return
        val pos = runnerPosInDim[dim] ?: return
        outbound.post(MatchEvent.CompassUpdate(pos, runnerPosInDim, huntersUuid))
    }
}
