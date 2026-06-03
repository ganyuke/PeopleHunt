package io.github.ganyuke.peoplehunt.paper.listeners

import io.github.ganyuke.peoplehunt.core.events.ReportableEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.paper.utils.post
import io.github.ganyuke.peoplehunt.paper.utils.toMatchPlayer
import org.bukkit.attribute.Attribute
import org.bukkit.entity.EnderDragon
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityRegainHealthEvent
import kotlin.uuid.toKotlinUuid

class HealthRegainListener(private val inbound: ReportableEventBus) : Listener {
    @EventHandler
    fun onEntityRegainHealth(event: EntityRegainHealthEvent) {
        val entity = event.entity as? LivingEntity ?: return
        val cause = event.regainReason.toString()
        val newHealth = entity.health
        val maxHealth = entity.getAttribute(Attribute.MAX_HEALTH)?.value ?: return

        when (entity) {
            is Player -> {
                inbound.post(
                    ReportablePayload.PlayerHealthRegained(
                        player = entity.toMatchPlayer(),
                        newHealth = newHealth,
                        maxHealth = maxHealth,
                        cause = cause,
                    )
                )
            }
            is EnderDragon -> {
                inbound.post(
                    ReportablePayload.EntityHealthRegained(
                        entityUuid = entity.uniqueId.toKotlinUuid(),
                        entityType = entity.type.key.toString(),
                        newHealth = newHealth,
                        maxHealth = maxHealth,
                        cause = cause,
                    )
                )
            }
        }
    }
}
