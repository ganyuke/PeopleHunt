package io.github.ganyuke.peoplehunt.paper.listeners

import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.events.ReportableEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.core.events.models.MatchPlayer
import io.github.ganyuke.peoplehunt.core.events.models.Velocity
import io.github.ganyuke.peoplehunt.paper.utils.post
import io.github.ganyuke.peoplehunt.paper.utils.toMatchPlayer
import io.github.ganyuke.peoplehunt.paper.utils.toPos4
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask

class ProjectileTracker(
    private val plugin: JavaPlugin,
    private val inbound: ReportableEventBus,
) : Listener {
    private var matchActive = false
    private var pollTask: BukkitTask? = null
    private val tracked = HashMap<Int, TrackedProjectile>()
    private val projectileDamage = HashMap<Int, Double>()

    private data class TrackedProjectile(
        val type: String,
        val shooter: MatchPlayer?,
        val shooterIdentifier: String?,
    )

    fun onMatchEvent(event: MatchEvent) {
        when (event) {
            is MatchEvent.MatchStart -> {
                matchActive = true
                tracked.clear()
                projectileDamage.clear()
                startPolling()
            }

            is MatchEvent.MatchEnd -> {
                matchActive = false
                tracked.clear()
                projectileDamage.clear()
                stopPolling()
            }

            else -> {}
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onProjectileLaunch(event: ProjectileLaunchEvent) {
        val projectile = event.entity
        val id = projectile.entityId

        val shooter = projectile.shooter
        val matchPlayer = (shooter as? Player)?.toMatchPlayer()
        val shooterIdentifier = (shooter as? LivingEntity)
            ?.takeIf { it !is Player }
            ?.type?.key?.toString()
        val type = projectile.type.key.toString()

        tracked[id] = TrackedProjectile(type, matchPlayer, shooterIdentifier)

        inbound.post(
            ReportablePayload.ProjectileLaunched(
                projectileId = id,
                shooter = matchPlayer,
                shooterIdentifier = shooterIdentifier,
                projectileType = type,
                launchPos = projectile.location.toPos4(),
                velocity = Velocity(projectile.velocity.x, projectile.velocity.y, projectile.velocity.z),
            )
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onProjectileDamage(event: EntityDamageByEntityEvent) {
        val projectile = event.damager as? Projectile ?: return
        if (projectile.entityId !in tracked) return
        projectileDamage[projectile.entityId] = event.finalDamage
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onProjectileHit(event: ProjectileHitEvent) {
        val projectile = event.entity
        val id = projectile.entityId

        val entry = tracked.remove(id)
        val cachedDamage = projectileDamage.remove(id)

        val damage = cachedDamage
            ?: (projectile as? AbstractArrow)?.damage
            ?: 0.0

        val hitEntity = event.hitEntity
        val hitPlayer = (hitEntity as? Player)?.toMatchPlayer()

        inbound.post(
            ReportablePayload.ProjectileHit(
                projectileId = id,
                shooter = entry?.shooter,
                shooterIdentifier = entry?.shooterIdentifier,
                projectileType = entry?.type ?: projectile.type.key.toString(),
                hitPos = projectile.location.toPos4(),
                hitEntityIdentifier = hitEntity?.type?.key?.toString(),
                hitPlayer = hitPlayer,
                damage = damage,
            )
        )
    }

    private fun startPolling() {
        stopPolling()
        pollTask = object : BukkitRunnable() {
            override fun run() {
                val it = tracked.iterator()
                while (it.hasNext()) {
                    val id = it.next().key
                    val projectile = findProjectile(id) ?: run { it.remove(); continue }

                    inbound.post(
                        ReportablePayload.ProjectileMoved(
                            projectileId = id,
                            pos = projectile.location.toPos4(),
                            velocity = Velocity(projectile.velocity.x, projectile.velocity.y, projectile.velocity.z),
                        )
                    )
                }
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }

    private fun stopPolling() {
        pollTask?.cancel()
        pollTask = null
    }

    private fun findProjectile(entityId: Int): Projectile? {
        for (world in plugin.server.worlds) {
            for (entity in world.entities) {
                if (entity is Projectile && entity.entityId == entityId) {
                    return if (entity.isValid) entity else null
                }
            }
        }
        return null
    }
}
