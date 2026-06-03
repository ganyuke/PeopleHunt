package io.github.ganyuke.peoplehunt.paper.listeners

import io.github.ganyuke.peoplehunt.core.events.ReportableEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.paper.utils.post
import io.github.ganyuke.peoplehunt.paper.utils.toMatchPlayer
import io.papermc.paper.datacomponent.DataComponentTypes
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerItemConsumeEvent

class FoodTracker(private val inbound: ReportableEventBus) : Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onItemConsume(event: PlayerItemConsumeEvent) {
        val item = event.item
        val foodProps = item.getData(DataComponentTypes.FOOD) ?: return

        inbound.post(
            ReportablePayload.PlayerConsumedItem(
                player = event.player.toMatchPlayer(),
                itemType = item.type.key.toString(),
                hungerRestored = foodProps.nutrition(),
                saturationRestored = foodProps.saturation(),
            )
        )
    }
}
