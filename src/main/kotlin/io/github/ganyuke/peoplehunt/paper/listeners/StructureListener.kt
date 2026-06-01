package io.github.ganyuke.peoplehunt.paper.listeners

import io.github.ganyuke.peoplehunt.core.events.ReportableEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.paper.utils.StructureLocator
import io.github.ganyuke.peoplehunt.paper.utils.post
import io.github.ganyuke.peoplehunt.paper.utils.toMatchPlayer
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import java.util.UUID

class StructureListener(private val inbound: ReportableEventBus) : Listener {
    val diffChecker = HashMap<UUID, String?>()

    @EventHandler
    fun onPlayerMoveCheckStructure(event: PlayerMoveEvent) {
        if (!event.hasChangedBlock()) return

        val structure = StructureLocator.getStructureAt(event.to)
        val prevStructure = diffChecker[event.player.uniqueId]

        // only send events on transitions
        if (prevStructure == structure) return

        val player = event.player.toMatchPlayer()

        // transition from previous structure means we are leaving the previous structure
        if (prevStructure != null) {
            inbound.post(ReportablePayload.PlayerExitedStructure(player, prevStructure))
        }

        // transition to next structure means we are entering a new structure
        if (structure != null) {
            inbound.post(ReportablePayload.PlayerEnteredStructure(player, structure))
        }

        diffChecker[event.player.uniqueId] = structure
    }
}