package io.github.ganyuke.peoplehunt.paper.listeners

import io.github.ganyuke.peoplehunt.core.events.ReportableEvent
import io.github.ganyuke.peoplehunt.core.events.ReportableEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.core.events.models.KillCause
import io.github.ganyuke.peoplehunt.paper.utils.StructureLocator
import io.github.ganyuke.peoplehunt.paper.utils.post
import io.github.ganyuke.peoplehunt.paper.utils.toMatchPlayer
import io.github.ganyuke.peoplehunt.paper.utils.toPos4
import io.github.ganyuke.peoplehunt.paper.utils.toSnapshot
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerRespawnEvent

class CoreListener(private val inbound: ReportableEventBus) : Listener {
    @EventHandler
    fun onMove(event: PlayerMoveEvent) {
        inbound.post(event.toSnapshot())
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDeath(event: EntityDeathEvent) {
        val cause = event.entity.lastDamageCause as? EntityDamageByEntityEvent

        val killCause = when (val damager = cause?.damager) {
            is Player -> KillCause.KilledByPlayer(damager.toMatchPlayer())
            is Entity -> KillCause.KilledByEntity(damager.type.key.toString())
            null -> KillCause.Environmental
        }

        val deadEntity = event.entity
        if (deadEntity is Player) {
            inbound.post(
                ReportablePayload.PlayerDied(
                    player = deadEntity.toMatchPlayer(),
                    pos = deadEntity.location.toPos4(),
                    cause = killCause,
                    deathMessage = (event as? PlayerDeathEvent)?.deathMessage,
                )
            )
        } else {
            inbound.post(
                ReportablePayload.EntityDied(
                    entityIdentifier = deadEntity.type.key.toString(),
                    pos = deadEntity.location.toPos4(),
                    cause = killCause,
                )
            )
        }
    }

    @EventHandler
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player

        inbound.post(
            ReportablePayload.PlayerRespawned(
                player = player.toMatchPlayer(),
                pos = player.location.toPos4()
            )
        )
    }
}
