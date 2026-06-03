package io.github.ganyuke.peoplehunt.paper.listeners

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent
import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.events.ReportableEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.paper.utils.post
import io.github.ganyuke.peoplehunt.paper.utils.toMatchPlayer
import io.github.ganyuke.peoplehunt.paper.utils.toPos4
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.world.PortalCreateEvent

class LandmarkTracker(private val inbound: ReportableEventBus) : Listener {
    private var worldSpawnRecorded = false

    fun onMatchEvent(event: MatchEvent) {
        when (event) {
            is MatchEvent.MatchStart -> {
                worldSpawnRecorded = false
                recordWorldSpawn()
            }

            is MatchEvent.MatchEnd -> {
                worldSpawnRecorded = false
            }

            else -> {}
        }
    }

    private fun recordWorldSpawn() {
        if (worldSpawnRecorded) return
        worldSpawnRecorded = true
        val world = Bukkit.getWorlds().firstOrNull() ?: return
        inbound.post(
            ReportablePayload.WorldSpawnRecorded(
                pos = world.spawnLocation.toPos4(),
            )
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPortalCreate(event: PortalCreateEvent) {
        if (event.reason != PortalCreateEvent.CreateReason.FIRE) return
        val firstBlock = event.blocks.firstOrNull() ?: return

        inbound.post(
            ReportablePayload.NetherPortalCreated(
                pos = firstBlock.location.toPos4(),
            )
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSetSpawn(event: PlayerSetSpawnEvent) {
        val location = event.location ?: return

        inbound.post(
            ReportablePayload.PlayerSetSpawn(
                player = event.player.toMatchPlayer(),
                pos = location.toPos4(),
                spawnType = event.cause.toString(),
            )
        )
    }
}
