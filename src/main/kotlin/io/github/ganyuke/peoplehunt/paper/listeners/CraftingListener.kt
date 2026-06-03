package io.github.ganyuke.peoplehunt.paper.listeners

import io.github.ganyuke.peoplehunt.core.events.ReportableEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.paper.utils.post
import io.github.ganyuke.peoplehunt.paper.utils.toMatchPlayer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerItemBreakEvent
import org.bukkit.inventory.view.AnvilView

class CraftingListener(private val inbound: ReportableEventBus) : Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCraftItem(event: CraftItemEvent) {
        val player = event.whoClicked as? Player ?: return
        val result = event.recipe.result
        if (result.type.isAir) return

        inbound.post(
            ReportablePayload.PlayerCraftedItem(
                player = player.toMatchPlayer(),
                itemType = result.type.key.toString(),
                amount = result.amount,
            )
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onItemBreak(event: PlayerItemBreakEvent) {
        inbound.post(
            ReportablePayload.PlayerItemBroke(
                player = event.player.toMatchPlayer(),
                itemType = event.brokenItem.type.key.toString(),
            )
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onAnvilResultTaken(event: InventoryClickEvent) {
        if (event.inventory.type != InventoryType.ANVIL) return
        if (event.slotType != InventoryType.SlotType.RESULT) return

        val current = event.currentItem ?: return
        if (current.type.isAir) return

        val player = event.whoClicked as? Player ?: return
        val anvilView = event.view as? AnvilView ?: return
        if (anvilView.repairCost <= 0) return

        val anvilInv = anvilView.topInventory
        val firstItem = anvilInv.firstItem ?: return
        val secondItem = anvilInv.secondItem
        val isRenameOnly = secondItem == null && current.type == firstItem.type
        if (isRenameOnly) return

        inbound.post(
            ReportablePayload.PlayerRepairedItem(
                player = player.toMatchPlayer(),
                itemType = current.type.key.toString(),
            )
        )
    }
}
