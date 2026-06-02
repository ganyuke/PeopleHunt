package io.github.ganyuke.peoplehunt.paper.listeners

import io.github.ganyuke.peoplehunt.core.events.ReportableEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.core.events.models.MatchPlayer
import io.github.ganyuke.peoplehunt.paper.utils.post
import io.github.ganyuke.peoplehunt.paper.utils.toMatchPlayer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPotionEffectEvent
import org.bukkit.event.entity.EntityPotionEffectEvent.Action.*
import org.bukkit.potion.PotionEffect

class PotionEffectListener(private val inbound: ReportableEventBus) : Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPotionEffect(event: EntityPotionEffectEvent) {
        val player = (event.entity as? Player)?.toMatchPlayer() ?: return
        val effectType = event.modifiedType.key.toString()
        val cause = event.cause.name

        when (event.action) {
            ADDED -> applyEffect(player, effectType, cause, event.newEffect ?: return, reapplication = false)
            CHANGED -> if (event.isOverride) applyEffect(
                player,
                effectType,
                cause,
                event.newEffect ?: return,
                reapplication = true
            )

            REMOVED, CLEARED -> inbound.post(ReportablePayload.PotionEffectRemoved(player, effectType, cause))
        }
    }

    private fun applyEffect(
        player: MatchPlayer,
        effectType: String,
        cause: String,
        effect: PotionEffect,
        reapplication: Boolean
    ) {
        inbound.post(
            ReportablePayload.PotionEffectApplied(
                player,
                effectType,
                effect.amplifier,
                effect.duration,
                cause,
                reapplication
            )
        )
    }
}
