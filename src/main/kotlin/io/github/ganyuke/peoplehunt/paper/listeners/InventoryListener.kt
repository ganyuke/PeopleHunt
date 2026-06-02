package io.github.ganyuke.peoplehunt.paper.listeners

import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.events.ReportableEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.paper.utils.post
import io.github.ganyuke.peoplehunt.paper.utils.toMatchPlayer
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.Base64
import java.util.UUID

class InventoryListener(
    private val plugin: JavaPlugin,
    private val inbound: ReportableEventBus,
) : Listener {
    private var matchActive = false
    private var resyncTask: BukkitTask? = null
    private val lastKeyframe = HashMap<UUID, List<String>>()

    fun onMatchEvent(event: MatchEvent) {
        when (event) {
            is MatchEvent.MatchStart -> {
                matchActive = true
                lastKeyframe.clear()
                plugin.server.onlinePlayers.forEach { seedKeyframe(it) }
                resyncTask = object : BukkitRunnable() {
                    override fun run() {
                        plugin.server.onlinePlayers.forEach { syncInventory(it) }
                    }
                }.runTaskTimer(plugin, 100L, 100L)
            }

            is MatchEvent.MatchEnd -> {
                matchActive = false
                resyncTask?.cancel()
                resyncTask = null
                lastKeyframe.clear()
            }

            else -> {}
        }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (matchActive) seedKeyframe(event.player)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        lastKeyframe.remove(event.player.uniqueId)
    }

    @EventHandler
    fun onSlotChanged(event: PlayerInventorySlotChangeEvent) {
        inbound.post(
            ReportablePayload.InventoryDelta(
                player = event.player.toMatchPlayer(),
                slot = event.slot,
                item = serializeItem(event.newItemStack),
            )
        )
    }

    @EventHandler
    fun onHandChanged(event: PlayerItemHeldEvent) {
        inbound.post(
            ReportablePayload.MainHandChanged(
                player = event.player.toMatchPlayer(),
                slot = event.newSlot,
            )
        )
    }

    private fun seedKeyframe(player: Player) {
        val slots = serializeInventory(player.inventory)
        lastKeyframe[player.uniqueId] = slots
        inbound.post(
            ReportablePayload.InventoryKeyframe(
                player = player.toMatchPlayer(),
                contents = slots,
                heldItemSlot = player.inventory.heldItemSlot,
            )
        )
    }

    private fun syncInventory(player: Player) {
        val slots = serializeInventory(player.inventory)
        val previous = lastKeyframe[player.uniqueId]
        if (slots == previous) return
        lastKeyframe[player.uniqueId] = slots
        inbound.post(
            ReportablePayload.InventoryKeyframe(
                player = player.toMatchPlayer(),
                contents = slots,
                heldItemSlot = player.inventory.heldItemSlot,
            )
        )
    }

    private fun serializeInventory(inventory: PlayerInventory): List<String> {
        return inventory.contents.map { item -> serializeItem(item) }
    }

    private fun serializeItem(item: ItemStack?): String {
        if (item == null || item.type.isAir) return ""
        return Base64.getEncoder().encodeToString(item.serializeAsBytes())
    }
}
