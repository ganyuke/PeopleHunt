package io.github.ganyuke.peoplehunt.paper.listeners

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerMoveEvent
import io.github.ganyuke.peoplehunt.core.Utils.Pos4
import io.github.ganyuke.peoplehunt.core.events.ReportableEvent
import io.github.ganyuke.peoplehunt.core.events.ReportableEventBus

class PaperListener(private val inbound: ReportableEventBus) : Listener {

    @EventHandler
    fun onMove(event: PlayerMoveEvent) {
        if (!event.hasChangedBlock()) return
        val p = event.player
        val pos = Pos4(
            p.location.blockX,
            p.location.blockY,
            p.location.blockZ,
            p.world.uid.toKotlinUuid(),
        )
        inbound.post(ReportableEvent.PlayerMoved(p.uniqueId.toKotlinUuid(), pos))
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDeath(event: EntityDeathEvent) {
        val loc = event.entity.location
        val pos = Pos4(loc.blockX, loc.blockY, loc.blockZ, loc.world.uid.toKotlinUuid())
        inbound.post(ReportableEvent.EntityDied(
            player           = (event.entity as? Player)?.uniqueId?.toKotlinUuid(),
            entityIdentifier = event.entityType.key.toString(),
            pos              = pos,
            playerKiller     = event.entity.killer?.uniqueId?.toKotlinUuid(),
            entityKiller     = null,
        ))
    }
}
