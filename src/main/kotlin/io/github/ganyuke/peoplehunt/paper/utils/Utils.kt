package io.github.ganyuke.peoplehunt.paper.utils

import io.github.ganyuke.peoplehunt.core.events.ReportableEvent
import io.github.ganyuke.peoplehunt.core.events.ReportableEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.core.events.models.MatchPlayer
import io.github.ganyuke.peoplehunt.core.events.models.Pos4
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerMoveEvent
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

// put all the utils for Paper-centric helpers in this bukkit
// sorry i mean bucket not bukkit, my bad
fun Pos4.toLocation(): Location? {
    // if the world returns null... um. well I don't think I can even do anything about that.
    // why doesn't your world exist? are you playing a manhunt on a non-existent world??
    val world = Bukkit.getWorld(w.toJavaUuid()) ?: return null
    return Location(world, x.toDouble(), y.toDouble(), z.toDouble())
}

fun Location.toPos4(): Pos4 = Pos4(blockX, blockY, blockZ, world.uid.toKotlinUuid())

fun Player.toMatchPlayer() = MatchPlayer(
    uuid = this.uniqueId.toKotlinUuid(),
    name = this.name
)

fun PlayerMoveEvent.toSnapshot() = ReportablePayload.PlayerMoved(
    this.player.toMatchPlayer(),
    this.to.toPos4(),
    this.to.yaw,
    this.to.pitch,
    this.player.isSprinting,
    this.player.isSneaking,
    this.player.isFlying,
    this.player.isSwimming,
    this.player.isGliding
)

fun ReportableEventBus.post(payload: ReportablePayload) = this.post(
    ReportableEvent(
        tick = Bukkit.getServer().currentTick,
        payload = payload
    )
)