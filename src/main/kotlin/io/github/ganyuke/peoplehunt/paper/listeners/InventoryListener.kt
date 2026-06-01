package io.github.ganyuke.peoplehunt.paper.listeners

import io.github.ganyuke.peoplehunt.core.events.ReportableEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.paper.utils.post
import io.github.ganyuke.peoplehunt.paper.utils.toMatchPlayer
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.inventory.ItemStack

class InventoryListener(private val inbound: ReportableEventBus) : Listener {
    @EventHandler
    fun onSlotChanged(event: PlayerInventorySlotChangeEvent) {
        val payload = ReportablePayload.InventoryDelta(
            player = event.player.toMatchPlayer(),
            slot = event.slot,
            itemHex = event.newItemStack.serializeAsBytes().toHexString()
        )
        inbound.post(payload)
    }

    @EventHandler
    fun onHandChanged(event: PlayerItemHeldEvent) {
        val payload = ReportablePayload.MainHandChanged(
            player = event.player.toMatchPlayer(),
            slot = event.newSlot
        )
        inbound.post(payload)
    }

    private fun ItemStack?.toHex(): String = when {
        this == null || type.isAir -> "AIR"
        else -> HexFormat.of().formatHex(serializeAsBytes())
    }
}