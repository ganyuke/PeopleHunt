package io.github.ganyuke.peoplehunt.core.services.core

import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.events.MatchEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportableEvent
import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.core.events.models.Pos4
import kotlin.uuid.Uuid

class CompassService(private val outbound: MatchEventBus) {
    private var runnerUuid: Uuid? = null
    private var runnerDim: Uuid? = null
    private var huntersUuid: Set<Uuid> = emptySet()
    private val runnerPosInDim: MutableMap<Uuid, Pos4> = mutableMapOf()

    fun onReportableEvent(event: ReportableEvent) : Unit =
        when (val payload = event.payload) {
            // feature: update tracked position on runner movement in dimension
            is ReportablePayload.PlayerMovedByBlock -> {
                if (payload.player.uuid != runnerUuid) return

                runnerDim = payload.pos.w
                runnerPosInDim[payload.pos.w] = payload.pos
            }

            // feature: give compass on hunter respawn
            is ReportablePayload.PlayerRespawned -> {
                if (payload.player.uuid !in huntersUuid) return
                outbound.post(MatchEvent.GiveHuntersCompass(setOf(payload.player.uuid)))
            }

            else -> {}
        }

    fun onMatchEvent(event: MatchEvent) =
        when (event) {
            is MatchEvent.MatchStart -> {
                runnerUuid = event.result.runner.uuid
                huntersUuid = event.result.hunters.map { it.uuid }.toSet()
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
