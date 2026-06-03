package io.github.ganyuke.peoplehunt.paper.listeners

import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.events.ReportableEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.core.events.models.MobSnapshot
import io.github.ganyuke.peoplehunt.paper.utils.post
import io.github.ganyuke.peoplehunt.paper.utils.toMatchPlayer
import io.github.ganyuke.peoplehunt.paper.utils.toPos4
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import kotlin.uuid.toKotlinUuid

class MobTracker(
    private val plugin: JavaPlugin,
    private val inbound: ReportableEventBus,
) : Listener {
    private var matchActive = false
    private var pollTask: BukkitTask? = null

    companion object {
        private const val SCAN_RADIUS = 16.0
        private const val POLL_INTERVAL = 40L
    }

    fun onMatchEvent(event: MatchEvent) {
        when (event) {
            is MatchEvent.MatchStart -> {
                matchActive = true
                startPolling()
            }

            is MatchEvent.MatchEnd -> {
                matchActive = false
                stopPolling()
            }

            else -> {}
        }
    }

    private fun startPolling() {
        stopPolling()
        pollTask = object : BukkitRunnable() {
            override fun run() {
                if (!matchActive) return
                for (player in plugin.server.onlinePlayers) {
                    scanNearbyMobs(player)
                }
            }
        }.runTaskTimer(plugin, 0L, POLL_INTERVAL)
    }

    private fun stopPolling() {
        pollTask?.cancel()
        pollTask = null
    }

    private fun scanNearbyMobs(player: Player) {
        val loc = player.location
        val nearby = loc.getNearbyLivingEntities(SCAN_RADIUS) { entity ->
            entity !is Player && entity.isValid
        }

        val mobs = nearby.map { entity ->
            MobSnapshot(
                entityUuid = entity.uniqueId.toKotlinUuid(),
                pos = entity.location.toPos4(),
                entityType = entity.type.key.toString(),
                health = entity.health.coerceAtLeast(0.0),
                distance = entity.location.distanceSquared(loc),
            )
        }

        if (mobs.isEmpty()) return

        inbound.post(
            ReportablePayload.NearbyMobs(
                player = player.toMatchPlayer(),
                mobs = mobs,
            )
        )
    }
}
