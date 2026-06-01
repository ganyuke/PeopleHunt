package io.github.ganyuke.peoplehunt.paper.listeners

import io.github.ganyuke.peoplehunt.core.events.ReportableEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.core.events.models.FluidState
import io.github.ganyuke.peoplehunt.paper.utils.post
import io.github.ganyuke.peoplehunt.paper.utils.toMatchPlayer
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import kotlin.time.Clock

class FluidListener(private val plugin: JavaPlugin, private val inbound: ReportableEventBus) : Listener {
    private val playerStates = HashMap<UUID, FluidState>()
    private val activeTasks = HashMap<UUID, BukkitTask>()

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMoveIntoFluid(event: PlayerMoveEvent) {
        val player = event.player
        val head = player.eyeLocation
        val now = Clock.System.now()

        val newState: FluidState = when {
            player.isInWater && head.block.isLiquid -> FluidState.SubmergedInWater(now)
            player.location.block.type == Material.LAVA -> FluidState.InLava(now)
            player.location.block.isSolid -> FluidState.SuffocatingInBlock(now)
            else -> FluidState.Dry
        }

        val prev = playerStates[player.uniqueId] ?: FluidState.Dry
        if (newState::class == prev::class) return  // no transition, skip

        playerStates[player.uniqueId] = newState
        handleTransition(player, prev, newState)
    }

    private fun handleTransition(player: Player, from: FluidState, to: FluidState) {
        // any transition to/from dry is either an exit or enter of fluid
        if (from !is FluidState.Dry) inbound.post(ReportablePayload.PlayerExitedFluid(player.toMatchPlayer(), from))
        if (to !is FluidState.Dry) inbound.post(ReportablePayload.PlayerEnteredFluid(player.toMatchPlayer(), to))

        // side effect: if submerged in water or suffocating, start polling
        when (to) {
            is FluidState.SubmergedInWater,
            is FluidState.SuffocatingInBlock -> startBreathPoller(player)

            else -> activeTasks.remove(player.uniqueId)?.cancel()
        }
    }

    private fun startBreathPoller(player: Player) {
        activeTasks[player.uniqueId]?.cancel()
        activeTasks[player.uniqueId] = object : BukkitRunnable() {
            var lastAir = player.remainingAir
            override fun run() {
                if (!player.isOnline) {
                    cancel(); return
                }
                val air = player.remainingAir
                if (air != lastAir) {
                    inbound.post(
                        ReportablePayload.PlayerBreathChanged(
                            player.toMatchPlayer(),
                            air,
                            player.maximumAir
                        )
                    )
                    lastAir = air
                }
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }
}
