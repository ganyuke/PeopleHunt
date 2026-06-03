package io.github.ganyuke.peoplehunt.paper.listeners

import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.events.ReportableEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.core.events.SpawnType
import io.github.ganyuke.peoplehunt.paper.utils.post
import io.github.ganyuke.peoplehunt.paper.utils.toMatchPlayer
import io.github.ganyuke.peoplehunt.paper.utils.toPos4
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.data.type.RespawnAnchor
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerBedEnterEvent
import org.bukkit.event.player.PlayerBedEnterEvent.BedEnterResult
import org.bukkit.event.player.PlayerInteractEvent
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
    fun onBedEnter(event: PlayerBedEnterEvent) {
        if (event.bedEnterResult != BedEnterResult.OK) return
        val bed = event.bed
        inbound.post(
            ReportablePayload.PlayerSetSpawn(
                player = event.player.toMatchPlayer(),
                pos = bed.location.toPos4(),
                spawnType = SpawnType.BED,
            )
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onRespawnAnchorInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        val block = event.clickedBlock ?: return
        if (block.type != Material.RESPAWN_ANCHOR) return

        val anchorData = block.blockData as? RespawnAnchor ?: return
        if (anchorData.charges <= 0) return

        inbound.post(
            ReportablePayload.PlayerSetSpawn(
                player = event.player.toMatchPlayer(),
                pos = block.location.toPos4(),
                spawnType = SpawnType.RESPAWN_ANCHOR,
            )
        )
    }
}
