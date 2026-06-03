package io.github.ganyuke.peoplehunt.paper.listeners

import io.github.ganyuke.peoplehunt.core.events.ReportableEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.paper.utils.StructureLocator
import io.github.ganyuke.peoplehunt.paper.utils.post
import io.github.ganyuke.peoplehunt.paper.utils.toMatchPlayer
import io.github.ganyuke.peoplehunt.paper.utils.toPos4
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import java.util.*

class StructureListener(private val inbound: ReportableEventBus) : Listener {
    val diffChecker = HashMap<UUID, String?>()

    @EventHandler
    fun onPlayerMoveCheckStructure(event: PlayerMoveEvent) {
        if (!event.hasChangedBlock()) return

        val structureInfo = StructureLocator.getStructureInfoAt(event.to)
        val structure = structureInfo?.name
        val prevStructure = diffChecker[event.player.uniqueId]

        // only send events on transitions
        if (prevStructure == structure) return

        val player = event.player.toMatchPlayer()

        // transition from previous structure means we are leaving the previous structure
        if (prevStructure != null) {
            inbound.post(ReportablePayload.PlayerExitedStructure(player, prevStructure))
        }

        // transition to next structure means we are entering a new structure
        if (structureInfo != null) {
            inbound.post(ReportablePayload.PlayerEnteredStructure(player, structureInfo.name, structureInfo.center.toPos4()))
        }

        diffChecker[event.player.uniqueId] = structure
    }
}