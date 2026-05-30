package io.github.ganyuke.peoplehunt.paper.listeners

import io.github.ganyuke.peoplehunt.core.events.ReportableEvent
import io.github.ganyuke.peoplehunt.core.events.ReportableEventBus
import io.github.ganyuke.peoplehunt.paper.utils.Utils.toMatchPlayer
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
            inbound.post(
                ReportableEvent.PlayerDamagedEntity(
                    player = damager.toMatchPlayer(),
                    amount = event.finalDamage
                )
            )
        }

        // scenario B: player takes damage
        if (victim is Player) {
            inbound.post(
                ReportableEvent.PlayerDamagedByEntity(
                    player = victim.toMatchPlayer(),
                    amount = event.finalDamage
                )
            )
        }
    }
}