package io.github.ganyuke.peoplehunt.paper.listeners

import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.events.ReportableEvent
import io.github.ganyuke.peoplehunt.core.events.ReportableEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.paper.utils.post
import io.github.ganyuke.peoplehunt.paper.utils.toPos4
import org.bukkit.World
import org.bukkit.attribute.Attribute
import org.bukkit.entity.EnderCrystal
import org.bukkit.entity.EnderDragon
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntitySpawnEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask

class EndFightTracker(
    private val plugin: JavaPlugin,
    private val inbound: ReportableEventBus,
) : Listener {
    private var matchActive = false
    private var pollTask: BukkitTask? = null
    private val knownCrystals = HashSet<Int>()
    private var endDiscovered = false

    fun onMatchEvent(event: MatchEvent) {
        when (event) {
            is MatchEvent.MatchStart -> {
                matchActive = true
                endDiscovered = false
                knownCrystals.clear()
            }

            is MatchEvent.MatchEnd -> {
                matchActive = false
                endDiscovered = false
                knownCrystals.clear()
                stopPolling()
            }

            else -> {}
        }
    }

    fun onReportableEvent(event: ReportableEvent) {
        if (!matchActive) return

        if (event.payload is ReportablePayload.PlayerChangedDimension) {
            val payload = event.payload
            if (payload.to == "minecraft:the_end" && !endDiscovered) {
                discoverEndCrystals()
                startPolling()
            }
        }
    }

    @EventHandler
    fun onEntitySpawn(event: EntitySpawnEvent) {
        if (!matchActive) return
        val crystal = event.entity as? EnderCrystal ?: return
        if (crystal.world.environment != World.Environment.THE_END) return
        if (knownCrystals.add(crystal.entityId)) {
            inbound.post(
                ReportablePayload.EndCrystalDiscovered(
                    pos = crystal.location.toPos4(),
                    crystalEntityId = crystal.entityId,
                )
            )
        }
    }

    private fun discoverEndCrystals() {
        endDiscovered = true
        val endWorld = plugin.server.worlds.firstOrNull { it.environment == World.Environment.THE_END } ?: return
        for (entity in endWorld.entities) {
            val crystal = entity as? EnderCrystal ?: continue
            if (knownCrystals.add(crystal.entityId)) {
                inbound.post(
                    ReportablePayload.EndCrystalDiscovered(
                        pos = crystal.location.toPos4(),
                        crystalEntityId = crystal.entityId,
                    )
                )
            }
        }
    }

    private fun startPolling() {
        stopPolling()
        pollTask = object : BukkitRunnable() {
            override fun run() {
                val endWorld = plugin.server.worlds.firstOrNull { it.environment == World.Environment.THE_END } ?: return
                val dragon = endWorld.entities.firstOrNull { it is EnderDragon } as? EnderDragon ?: return
                if (!dragon.isValid) return

                inbound.post(
                    ReportablePayload.DragonSnapshot(
                        pos = dragon.location.toPos4(),
                        health = dragon.health.coerceAtLeast(0.0),
                        maxHealth = dragon.getAttribute(Attribute.MAX_HEALTH)?.value ?: 200.0,
                    )
                )
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }

    private fun stopPolling() {
        pollTask?.cancel()
        pollTask = null
    }
}
