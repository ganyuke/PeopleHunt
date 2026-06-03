package io.github.ganyuke.peoplehunt.paper.listeners

import io.github.ganyuke.peoplehunt.core.events.ReportableEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.paper.utils.post
import io.github.ganyuke.peoplehunt.paper.utils.toMatchPlayer
import io.github.ganyuke.peoplehunt.paper.utils.toPos4
import org.bukkit.entity.Damageable
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent

/**
 * Listeners for combat stats reporting
 */
class CombatStatsListener(private val inbound: ReportableEventBus) : Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDamageByPlayer(event: EntityDamageByEntityEvent) {
        val damager = event.damager
        val victim = event.entity

        // scenario A: player deals damage
        if (damager is Player) {
            val entityIdentifier = victim.type.key.toString()

            val remainingHealth = if (victim is Damageable) {
                (victim.health - event.finalDamage).coerceAtLeast(0.0)
            } else null

            val weaponType = damager.inventory.itemInMainHand.type.key.toString()
            val projectileId = (event.damager as? Projectile)?.entityId

            inbound.post(
                ReportablePayload.PlayerDamagedEntity(
                    player = damager.toMatchPlayer(),
                    entityIdentifier = entityIdentifier,
                    amount = event.finalDamage,
                    remainingHealth = remainingHealth,
                    victimPos = victim.location.toPos4(),
                    weaponType = weaponType,
                    projectileId = projectileId,
                )
            )
        }

        // scenario B: player takes damage
        if (victim is Player) {
            val remainingHealth =
                (victim.health - event.finalDamage).coerceAtLeast(0.0)

            val weaponType = (event.damager as? Player)?.inventory?.itemInMainHand?.type?.key?.toString()
            val projectileId = (event.damager as? Projectile)?.entityId

            inbound.post(
                ReportablePayload.PlayerDamagedByEntity(
                    player = victim.toMatchPlayer(),
                    entityIdentifier = event.damager.type.key.toString(),
                    amount = event.finalDamage,
                    remainingHealth = remainingHealth,
                    weaponType = weaponType,
                    projectileId = projectileId,
                )
            )
        }
    }
}