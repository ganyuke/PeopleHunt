package io.github.ganyuke.peoplehunt.paper.adapters

import org.bukkit.Bukkit
import org.bukkit.Location
import io.github.ganyuke.peoplehunt.core.events.MatchEvent

class PaperCompassAdapter {
    fun onMatchEvent(event: MatchEvent) {
        if (event !is MatchEvent.CompassUpdate) return
        val targetWorld = Bukkit.getWorld(event.dim.toJavaUuid()) ?: return
        val target = Location(targetWorld, event.pos.x.toDouble(), event.pos.y.toDouble(), event.pos.z.toDouble())
        Bukkit.getOnlinePlayers()
            .filter { it.uniqueId.toKotlinUuid() != event.runner }
            .forEach { hunter ->
                hunter.compassTarget =
                    if (hunter.world.uid == targetWorld.uid) target
                    else hunter.world.spawnLocation
            }
    }
}
