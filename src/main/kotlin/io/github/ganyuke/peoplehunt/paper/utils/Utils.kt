package io.github.ganyuke.peoplehunt.paper.utils

import io.github.ganyuke.peoplehunt.core.Utils
import io.github.ganyuke.peoplehunt.core.services.core.MatchEngine
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

// put all the utils for Paper-centric helpers in this bukkit
// sorry i mean bucket not bukkit, my bad
object Utils {
    fun Utils.Pos4.toLocation(): Location? {
        // if the world returns null... um. well I don't think I can even do anything about that.
        // why doesn't your world exist? are you playing a manhunt on a non-existent world??
        val world = Bukkit.getWorld(w.toJavaUuid()) ?: return null
        return Location(world, x.toDouble(), y.toDouble(), z.toDouble())
    }

    fun Location.toPos4(): Utils.Pos4 = Utils.Pos4(blockX, blockY, blockZ, world.uid.toKotlinUuid())

    fun Player.toMatchPlayer() = MatchEngine.MatchPlayer(
        uuid = this.uniqueId.toKotlinUuid(),
        name = this.name
    )
}