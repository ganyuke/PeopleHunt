package io.github.ganyuke.peoplehunt.paper.listeners

import io.github.ganyuke.peoplehunt.core.events.ReportableEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.core.events.models.TeleportCause
import io.github.ganyuke.peoplehunt.paper.utils.post
import io.github.ganyuke.peoplehunt.paper.utils.toMatchPlayer
import io.github.ganyuke.peoplehunt.paper.utils.toPos4
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerTeleportEvent

class TeleportListener(
    private val inbound: ReportableEventBus
) : Listener {
    val distanceThreshold = 4.0

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onTeleportEvent(event: PlayerTeleportEvent) {
        val paperCause = event.cause

        // exclude stuff like Paper pushing out the player when standing on an End Frame and putting in an eye
        val distance = event.from.toVector().distance(event.to.toVector())
        if (paperCause == PlayerTeleportEvent.TeleportCause.UNKNOWN && distance < distanceThreshold) return

        val domainCause = paperCause.toDomain()
        inbound.post(
            ReportablePayload.TeleportSnapshot(
                event.player.toMatchPlayer(),
                event.from.toPos4(),
                event.to.toPos4(),
                domainCause
            )
        )
    }

    private fun PlayerTeleportEvent.TeleportCause.toDomain() = when (this) {
        PlayerTeleportEvent.TeleportCause.ENDER_PEARL -> TeleportCause.ENDER_PEARL
        PlayerTeleportEvent.TeleportCause.CONSUMABLE_EFFECT -> TeleportCause.CONSUMABLE_EFFECT // covers Chorus Fruit
        PlayerTeleportEvent.TeleportCause.COMMAND -> TeleportCause.COMMAND
        PlayerTeleportEvent.TeleportCause.END_PORTAL -> TeleportCause.END_PORTAL
        PlayerTeleportEvent.TeleportCause.NETHER_PORTAL -> TeleportCause.NETHER_PORTAL
        PlayerTeleportEvent.TeleportCause.END_GATEWAY -> TeleportCause.END_GATEWAY
        PlayerTeleportEvent.TeleportCause.SPECTATE -> TeleportCause.SPECTATOR_WARP
        else -> TeleportCause.UNKNOWN
    }
}