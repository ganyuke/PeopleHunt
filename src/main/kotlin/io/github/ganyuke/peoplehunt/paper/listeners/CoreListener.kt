package io.github.ganyuke.peoplehunt.paper.listeners

import io.github.ganyuke.peoplehunt.core.events.ReportableEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.core.events.models.KillCause
import io.github.ganyuke.peoplehunt.paper.utils.post
import io.github.ganyuke.peoplehunt.paper.utils.toMatchPlayer
import io.github.ganyuke.peoplehunt.paper.utils.toPos4
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerRespawnEvent

class CoreListener(private val inbound: ReportableEventBus) : Listener {
    val mm = MiniMessage.miniMessage()

    /**
     * listener function solely for runenr compass updates
     * and prime on start
     */
    @EventHandler
    fun onMoveByBlock(event: PlayerMoveEvent) {
        if (!event.hasChangedBlock()) return // more chaotic this way

        inbound.post(ReportablePayload.PlayerMovedByBlock(
            event.player.toMatchPlayer(),
            event.to.toPos4(),
            event.player.isSneaking
        ))
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDeath(event: EntityDeathEvent) {
        val cause = event.entity.lastDamageCause as? EntityDamageByEntityEvent

        val killCause = when (val damager = cause?.damager) {
            is Player -> KillCause.KilledByPlayer(damager.toMatchPlayer())
            is Entity -> KillCause.KilledByEntity(damager.type.key.toString())
            null -> KillCause.Environmental
        }

        val weaponType = when (val damager = cause?.damager) {
            is Player -> damager.inventory.itemInMainHand.type.key.toString()
            is Projectile -> (damager.shooter as? Player)?.inventory?.itemInMainHand?.type?.key?.toString()
            else -> null
        }

        val projectileId = (cause?.damager as? Projectile)?.entityId

        val deadEntity = event.entity
        if (deadEntity is Player) {
            inbound.post(
                ReportablePayload.PlayerDied(
                    player = deadEntity.toMatchPlayer(),
                    pos = deadEntity.location.toPos4(),
                    cause = killCause,
                    deathMessage = (event as? PlayerDeathEvent)?.deathMessage()?.let(this.mm::serialize)
                )
            )
        } else {
            inbound.post(
                ReportablePayload.EntityDied(
                    entityIdentifier = deadEntity.type.key.toString(),
                    pos = deadEntity.location.toPos4(),
                    cause = killCause,
                    weaponType = weaponType,
                    projectileId = projectileId,
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
