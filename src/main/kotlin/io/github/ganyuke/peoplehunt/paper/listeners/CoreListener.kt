package io.github.ganyuke.peoplehunt.paper.listeners

import io.github.ganyuke.peoplehunt.core.events.ReportableEvent
import io.github.ganyuke.peoplehunt.core.events.ReportableEventBus
import io.github.ganyuke.peoplehunt.paper.utils.StructureLocator
import io.github.ganyuke.peoplehunt.paper.utils.toMatchPlayer
import io.github.ganyuke.peoplehunt.paper.utils.toPos4
import io.github.ganyuke.peoplehunt.paper.utils.toSnapshot
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerRespawnEvent

class CoreListener(private val inbound: ReportableEventBus) : Listener {
    @EventHandler
    fun onMove(event: PlayerMoveEvent) {
        val snapshot = if (event.hasChangedBlock()) {
            val structure = StructureLocator.getStructureAt(event.to)
            event.toSnapshot(structure)
        } else {
            event.toSnapshot()
        }

        inbound.post(ReportableEvent.PlayerMoved(snapshot))
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDeath(event: EntityDeathEvent) {
        // EntityDeathEvent doesn't naturally have entity killers,
        // only players, so we need to check what last damaged it before death
        val cause = event.entity.lastDamageCause as? EntityDamageByEntityEvent
        val killer = cause?.damager

        inbound.post(
            ReportableEvent.EntityDied(
                player = (event.entity as? Player)?.toMatchPlayer(),
                entityIdentifier = event.entityType.key.toString(),
                pos = event.entity.location.toPos4(),
                playerKiller = (killer as? Player)?.toMatchPlayer(),
                entityKiller = killer?.takeIf { it !is Player }?.type?.key?.toString(),
            )
        )
    }

    @EventHandler
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player

        inbound.post(
            ReportableEvent.PlayerRespawned(
                player = player.toMatchPlayer(),
                pos = player.location.toPos4()
            )
        )
    }
}
