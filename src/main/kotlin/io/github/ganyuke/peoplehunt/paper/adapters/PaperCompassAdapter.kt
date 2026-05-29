package io.github.ganyuke.peoplehunt.paper.adapters

import org.bukkit.Bukkit
import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.paper.utils.Utils.toLocation
import io.github.ganyuke.peoplehunt.paper.items.HunterCompass
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.CompassMeta
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

class PaperCompassAdapter {
    private val globalCompass = true

    // unfortunately, this method will cause the compass to bob when in a player's hand
    // but on the bright side it won't override your compasses
    private fun handleLodestoneUpdate(hunter: Player, locations: Map<Uuid, Location?>, world: Uuid) {
        val hunterWorldId = hunter.world.uid.toKotlinUuid()
        hunter.inventory
            .filterNotNull()
            .forEach { item ->
                val meta = item.hunterCompassMeta() ?: return@forEach

                // must set to false to allow us to set arbitrary positions
                meta.isLodestoneTracked = false

                // null lodestone should in theory just cause the compass to spin
                // if I'm understanding the Bukkit API correctly
                meta.lodestone = if (hunterWorldId == world) {
                    locations[world] // point to current runner position in dimension
                } else {
                    locations[hunterWorldId] // point to last position in hunter's dimension
                }

                // must write the new meta back to the itemMeta for the server to actually
                // register the changes we made
                item.itemMeta = meta
            }
    }

    // this one is more invasive since it will override all your compasses
    // but at least it doesn't cause your compass to bob every compass tick
    private fun handleGlobalUpdate(hunter: Player, locations: Map<Uuid, Location?>, world: Uuid) {
        val hunterWorldId = hunter.world.uid.toKotlinUuid()

        hunter.compassTarget = if (hunterWorldId == world) {
            locations[world] // point to current runner position in dimension
        } else {
            locations[hunterWorldId] // point to last position in hunter's dimension
        } ?:hunter.world.spawnLocation
    }

    private fun handleCompassUpdate(event: MatchEvent.CompassUpdate) {
        // I don't want to make a crap ton of Locations for every loop
        // so we'll create them all at the start and hope we'll use all
        // of them
        val locations = event.runnerDims
            .mapValues { (_, dim) -> dim.toLocation() }
            .filterValues { it != null }

        // don't want to branch on every forEach so we will resolve the compass
        // behavior here. very elegant solution. ahhh I feel very smart.
        val compassFunction: (Player, Map<Uuid, Location?>, Uuid) -> Unit =
            if (globalCompass) ::handleGlobalUpdate else ::handleLodestoneUpdate

        // we want to run through the inventory of every online hunter
        // and update ALL the hunter compasses (to be consistent!) in
        // their inventory
        Bukkit.getOnlinePlayers()
            .filter { it.uniqueId.toKotlinUuid() in event.huntersUuids }
            .forEach{ compassFunction(it, locations, event.pos.w) }
    }

    private fun handleGiveCompasses(event: MatchEvent.GiveHuntersCompass) {
        Bukkit.getOnlinePlayers()
            .filter { it.uniqueId.toKotlinUuid() in event.huntersUuids }
            .forEach { hunter ->
                // Paper API is great, they have an API to just straight up give the items.
                // isn't that just great?
                hunter.give(HunterCompass.create())
            }
    }

    fun onMatchEvent(event: MatchEvent) {
        when (event) {
            is MatchEvent.CompassUpdate -> handleCompassUpdate(event)
            is MatchEvent.GiveHuntersCompass -> handleGiveCompasses(event)
            else -> {}
        }
    }

    private fun ItemStack.hunterCompassMeta(): CompassMeta? {
        if (type != Material.COMPASS) return null

        // attempt to get the compass meta. if this fails...
        // well it's probably not a compass
        val meta = itemMeta as? CompassMeta ?: return null

        // filter for a compass given by OUR plugin
        // i.e. it has the PDC flag that we gave the compass
        if (!HunterCompass.isHunterCompass(meta.persistentDataContainer)) return null

        return meta
    }
}