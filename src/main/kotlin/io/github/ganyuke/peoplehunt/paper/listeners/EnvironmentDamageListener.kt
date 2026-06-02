package io.github.ganyuke.peoplehunt.paper.listeners

import io.github.ganyuke.peoplehunt.core.events.ReportableEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.paper.utils.post
import io.github.ganyuke.peoplehunt.paper.utils.toMatchPlayer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent

class EnvironmentDamageListener(private val inbound: ReportableEventBus) : Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEnvironmentDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        // skip entity-vs-entity damage (handled by CombatStatsListener)
        if (event is EntityDamageByEntityEvent) return

        val remainingHealth = (player.health - event.finalDamage).coerceAtLeast(0.0)

        inbound.post(
            ReportablePayload.PlayerDamagedByEnvironment(
                player = player.toMatchPlayer(),
                cause = event.cause.name,
                amount = event.finalDamage,
                remainingHealth = remainingHealth,
            )
        )
    }
}
