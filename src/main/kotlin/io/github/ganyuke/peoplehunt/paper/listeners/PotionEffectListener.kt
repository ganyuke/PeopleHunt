package io.github.ganyuke.peoplehunt.paper.listeners

import io.github.ganyuke.peoplehunt.core.events.ReportableEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.paper.utils.post
import io.github.ganyuke.peoplehunt.paper.utils.toMatchPlayer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPotionEffectEvent

class PotionEffectListener(private val inbound: ReportableEventBus) : Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPotionEffect(event: EntityPotionEffectEvent) {
        if (event.entity !is Player) return
        val player = (event.entity as Player).toMatchPlayer()
        val effectType = event.modifiedType.key.toString()
        val cause = event.cause.name

        when (event.action) {
            EntityPotionEffectEvent.Action.ADDED -> {
                val effect = event.newEffect ?: return
                inbound.post(
                    ReportablePayload.PotionEffectApplied(
                        player = player,
                        effectType = effectType,
                        amplifier = effect.amplifier,
                        duration = effect.duration,
                        cause = cause,
                        reapplication = false,
                    )
                )
            }

            EntityPotionEffectEvent.Action.CHANGED -> {
                if (!event.isOverride) return
                val effect = event.newEffect ?: return
                inbound.post(
                    ReportablePayload.PotionEffectApplied(
                        player = player,
                        effectType = effectType,
                        amplifier = effect.amplifier,
                        duration = effect.duration,
                        cause = cause,
                        reapplication = true,
                    )
                )
            }

            EntityPotionEffectEvent.Action.REMOVED,
            EntityPotionEffectEvent.Action.CLEARED -> {
                inbound.post(
                    ReportablePayload.PotionEffectRemoved(
                        player = player,
                        effectType = effectType,
                        cause = cause,
                    )
                )
            }
        }
    }
}
