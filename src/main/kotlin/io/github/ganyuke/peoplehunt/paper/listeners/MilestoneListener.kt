package io.github.ganyuke.peoplehunt.paper.listeners

import io.github.ganyuke.peoplehunt.core.events.ReportableEvent
import io.github.ganyuke.peoplehunt.core.events.ReportableEventBus
import io.github.ganyuke.peoplehunt.core.services.reporting.milestones.SpeedrunMilestone
import io.github.ganyuke.peoplehunt.paper.utils.Utils.toMatchPlayer
import io.papermc.paper.event.player.PlayerTradeEvent
import org.bukkit.Material
import org.bukkit.entity.EnderCrystal
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.player.PlayerBucketFillEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.world.PortalCreateEvent

/**
 * Listeners to emit events for the MilestoneTracker.
 * Excludes the structure events since Paper does not support
 * a listener for stepping into a structure. Will have to hijack
 * the path sampler.
 */
class MilestoneListener(private val inbound: ReportableEventBus) : Listener {
    private fun Material.toMilestoneItem(): SpeedrunMilestone.ItemAcquired.Item? = when (this) {
        Material.IRON_INGOT -> SpeedrunMilestone.ItemAcquired.Item.IRON_INGOT
        Material.BUCKET -> SpeedrunMilestone.ItemAcquired.Item.BUCKET
        Material.ENDER_PEARL -> SpeedrunMilestone.ItemAcquired.Item.ENDER_PEARL
        Material.BLAZE_ROD -> SpeedrunMilestone.ItemAcquired.Item.BLAZE_ROD
        Material.ENDER_EYE -> SpeedrunMilestone.ItemAcquired.Item.EYE_OF_ENDER
        else -> null
    }

    /**
     * Listeners for item-related milestones
     */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPickup(event: EntityPickupItemEvent) {
        val player = event.entity as? Player ?: return
        val item = event.item.itemStack.type.toMilestoneItem() ?: return

        inbound.post(
            ReportableEvent.PlayerAcquiredItem(
                player = player.toMatchPlayer(),
                item = item,
                method = SpeedrunMilestone.AcquisitionMethod.PICKED_UP
            )
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCraft(event: CraftItemEvent) {
        val player = event.whoClicked as? Player ?: return
        val item = event.recipe.result.type.toMilestoneItem() ?: return

        inbound.post(
            ReportableEvent.PlayerAcquiredItem(
                player = player.toMatchPlayer(),
                item = item,
                method = SpeedrunMilestone.AcquisitionMethod.CRAFTED
            )
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onTrade(event: PlayerTradeEvent) { // Swapped to PlayerTradeEvent for actual item acquisition
        val player = event.player
        val item = event.trade.result.type.toMilestoneItem() ?: return

        inbound.post(
            ReportableEvent.PlayerAcquiredItem(
                player = player.toMatchPlayer(),
                item = item,
                method = SpeedrunMilestone.AcquisitionMethod.TRADED
            )
        )
    }

    /**
     * Listeners for world interaction related milestone
     */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDimensionChange(event: PlayerChangedWorldEvent) {
        val player = event.player
        inbound.post(
            ReportableEvent.PlayerChangedDimension(
                player = player.toMatchPlayer(),
                from = event.from.name,
                to = player.world.name
            )
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onThrow(event: ProjectileLaunchEvent) {
        val player = event.entity.shooter as? Player ?: return

        // Find out what kind of tracking string to emit based on the entity type
        val itemKey = event.entity.type.key.toString() // e.g., "minecraft:ender_pearl" or "minecraft:eye_of_ender"

        inbound.post(
            ReportableEvent.PlayerThrewItem(
                player = player.toMatchPlayer(),
                item = itemKey
            )
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBucketFill(event: PlayerBucketFillEvent) {
        inbound.post(
            ReportableEvent.PlayerFilledBucket(
                player = event.player.toMatchPlayer(),
                fluid = event.blockClicked.type.name // e.g., "LAVA" or "WATER"
            )
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEndCrystalDestroy(event: EntityDamageByEntityEvent) {
        val crystal = event.entity as? EnderCrystal ?: return
        val killer = event.damager as? Player

        inbound.post(
            ReportableEvent.EndCrystalDestroyed(
                player = killer?.toMatchPlayer()
            )
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEndPortalCreate(event: PortalCreateEvent) {
        // Enforce that it's an End Portal frame being finalized
        if (event.reason != PortalCreateEvent.CreateReason.END_PLATFORM) return

        val player = event.entity as? Player ?: return

        inbound.post(
            ReportableEvent.EndPortalCompleted(
                player = player.toMatchPlayer()
            )
        )
    }
}