package io.github.ganyuke.peoplehunt.paper.listeners

import io.github.ganyuke.peoplehunt.core.events.ReportableEvent
import io.github.ganyuke.peoplehunt.core.events.ReportableEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.paper.utils.post
import io.github.ganyuke.peoplehunt.paper.utils.toMatchPlayer
import org.bukkit.entity.Damageable
import org.bukkit.entity.Player
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
                // apparently the health doesn't update until after so I guess we need to manually calculate
                (victim.health - event.finalDamage).coerceAtLeast(0.0)
            } else null

            inbound.post(
                ReportablePayload.PlayerDamagedEntity(
                    player = damager.toMatchPlayer(),
                    entityIdentifier = entityIdentifier,
                    amount = event.finalDamage,
                    remainingHealth = remainingHealth
                )
            )
        }

        // scenario B: player takes damage
        if (victim is Player) {
            val remainingHealth =
                // apparently the health doesn't update until after so I guess we need to manually calculate
                (victim.health - event.finalDamage).coerceAtLeast(0.0)

            inbound.post(
                ReportablePayload.PlayerDamagedByEntity(
                    player = victim.toMatchPlayer(),
                    entityIdentifier = event.damager.type.key.toString(),
                    amount = event.finalDamage,
                    remainingHealth = remainingHealth
                )
            )
        }
    }
}