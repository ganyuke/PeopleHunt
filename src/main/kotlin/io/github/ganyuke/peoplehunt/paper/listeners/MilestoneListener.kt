package io.github.ganyuke.peoplehunt.paper.listeners

import io.github.ganyuke.peoplehunt.core.events.ReportableEvent
import io.github.ganyuke.peoplehunt.core.events.ReportableEventBus
import io.github.ganyuke.peoplehunt.core.services.reporting.milestones.SpeedrunMilestone
import io.github.ganyuke.peoplehunt.paper.utils.Utils.toMatchPlayer
import io.github.ganyuke.peoplehunt.paper.utils.Utils.toPos4
import io.papermc.paper.event.player.PlayerTradeEvent
import org.bukkit.Material
import org.bukkit.entity.EnderCrystal
import org.bukkit.entity.EnderSignal
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockFormEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.EntitySpawnEvent
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
    //    private val entityTypeRegistry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENTITY_TYPE)
    private val eyeThrowRange = 9.0 // count as player's if thrown within 3 blocks

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
                from = event.from.key.toString(),
                to = player.world.key.toString()
            )
        )
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onEyeOfEnderSpawn(event: EntitySpawnEvent) {
        if (event.entity !is EnderSignal) return

        // the Eye of Ender doesn't trigger a projectile event
        // nor does it trigger a PlayerInteractEvent if you right-click
        // toward the sky so we're going to do a bit of a hack here.
        // may be slightly inaccurate but it's not like this is in control
        // of the nukes so
        val spawnLoc = event.location
        val thrower = spawnLoc.world?.players?.filter { player ->
            player.location.distanceSquared(spawnLoc) < eyeThrowRange
        }?.minByOrNull { it.location.distanceSquared(spawnLoc) }

        thrower?.let {
            inbound.post(ReportableEvent.PlayerThrewEnderEye(it.toMatchPlayer()))
        }
    }

    // maybe save this for projectile tracking. ender_eye doesn't trigger this
    /*    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun onThrow(event: ProjectileLaunchEvent) {
            val player = event.entity.shooter as? Player ?: return

            // Find out what kind of tracking string to emit based on the entity type
            val itemKey = entityTypeRegistry.getKey(event.entity.type)?.toString() ?: return // e.g., "minecraft:ender_pearl"

            inbound.post(
                ReportableEvent.PlayerThrewItem(
                    player = player.toMatchPlayer(),
                    item = itemKey
                )
            )
        }*/

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBucketFill(event: PlayerBucketFillEvent) {
        val fluidString = when (val material = event.blockClicked.type) {
            Material.WATER -> "minecraft:water"
            Material.LAVA -> "minecraft:lava"
            else -> material.key.toString()
        }

        inbound.post(
            ReportableEvent.PlayerFilledBucket(
                player = event.player.toMatchPlayer(),
                fluid = fluidString
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
    fun onEndPortalCreate(event: BlockFormEvent) {
        if (event.newState.type == Material.END_PORTAL) {
            inbound.post(
                ReportableEvent.EndPortalCompleted(event.block.location.toPos4())
            )
        }
    }
}