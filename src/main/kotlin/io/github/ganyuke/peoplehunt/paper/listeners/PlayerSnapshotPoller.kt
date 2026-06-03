package io.github.ganyuke.peoplehunt.paper.listeners

import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.events.ReportableEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.core.events.models.*
import io.github.ganyuke.peoplehunt.paper.utils.environmentSnapshot
import io.github.ganyuke.peoplehunt.paper.utils.post
import io.github.ganyuke.peoplehunt.paper.utils.toMatchPlayer
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.*
import kotlin.uuid.toKotlinUuid

class PlayerSnapshotPoller(
    private val plugin: JavaPlugin,
    private val inbound: ReportableEventBus,
) : Listener {
    private var matchActive = false
    private val activeTasks = HashMap<UUID, BukkitTask>()

    fun onMatchEvent(event: MatchEvent) {
        when (event) {
            is MatchEvent.MatchStart -> {
                matchActive = true
                // even if they're not part of the match, it's cool to see
                // who else was watching, if they were a spectator or not.
                plugin.server.onlinePlayers.forEach { startPolling(it) }
            }

            is MatchEvent.MatchEnd -> {
                matchActive = false
                activeTasks.values.forEach { it.cancel() }
                activeTasks.clear()
            }

            else -> {}
        }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val payload = ReportablePayload.PlayerJoined(event.player.toMatchPlayer())
        inbound.post(payload)

        if (matchActive) startPolling(event.player)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        val matchPlayer = player.toMatchPlayer()

        inbound.post(ReportablePayload.PlayerSnapshotChanged(matchPlayer, player.snapshot()))
        inbound.post(ReportablePayload.PlayerQuit(matchPlayer, event.reason.name))

        stopPolling(event.player.uniqueId)
    }

    private fun startPolling(player: Player) {
        activeTasks.remove(player.uniqueId)?.cancel()
        activeTasks[player.uniqueId] = object : BukkitRunnable() {
            override fun run() {
                val payload = ReportablePayload.PlayerSnapshotChanged(
                    player = player.toMatchPlayer(),
                    snapshot = player.snapshot()
                )
                inbound.post(payload)
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }

    private fun stopPolling(uuid: UUID) {
        activeTasks.remove(uuid)?.cancel()
    }

    private fun Player.snapshot(): PlayerSnapshot {
        val player = this

        if (!player.isOnline) return PlayerSnapshot.Offline

        if (player.isDead) return PlayerSnapshot.Online(OnlineState.Dead)

        val loc = player.location
        val env = player.environmentSnapshot()

        val lifeSnapshot = CurrentLifeData(
            spatialData = SpatialData(
                position = Pos4(
                    x = loc.blockX,
                    y = loc.blockY,
                    z = loc.blockZ,
                    w = player.world.uid.toKotlinUuid(),
                ),
                yaw = loc.yaw,
                pitch = loc.pitch,
                velocity = Velocity(player.velocity.x, player.velocity.y, player.velocity.z)
            ),
            vitals = Vitals(
                health = player.health,
                maxHealth = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0,
                foodLevel = player.foodLevel,
                saturation = player.saturation,
                absorption = player.absorptionAmount,
                remainingAir = player.remainingAir,
                maxAir = player.maximumAir,
                experienceLevel = player.level,
                experienceProgress = player.exp,
                totalXpPoints = player.totalExperience,
            ),
            currentStates = CurrentStates(
                environmentFlags = EnvironmentFlags(
                    isBurning = player.fireTicks > 0,
                    isDrowning = player.remainingAir <= 0,
                    isSuffocating = env.isSuffocating,
                    isFreezing = player.isInPowderedSnow,
                    isWadingInWater = env.isWadingInWater,
                    isWadingInLava = env.isWadingInLava,
                    isSubmergedInWater = env.isSubmergedInWater,
                    isSubmergedInLava = env.isSubmergedInLava,
                    isInsideCobweb = env.isInsideCobweb,
                    isInsideSweetBerry = env.isInsideSweetBerry,
                ),
                movementFlags = MovementFlags(
                    isSleeping = player.isSleeping,
                    isRiptiding = player.isRiptiding,
                    isClimbing = player.isClimbing,
                    isSwimming = player.isSwimming,
                    isSprinting = player.isSprinting,
                    isSneaking = player.isSneaking,
                    isFlying = player.isFlying,
                    isGliding = player.isGliding,
                ),
                ridingVehicle = player.vehicle?.type?.key?.toString() ?: "none",
            ),
            metadata = LifeMetadata(
                gameMode = player.gameMode.name,
                activePotionEffects = player.activePotionEffects.map { effect ->
                    ActivePotionEffect(
                        type = effect.type.key.toString(),
                        amplifier = effect.amplifier,
                        duration = effect.duration,
                    )
                },
            ),
        )

        return PlayerSnapshot.Online(OnlineState.Alive(lifeSnapshot))
    }
}
