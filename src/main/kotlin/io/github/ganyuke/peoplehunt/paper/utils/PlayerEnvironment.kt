package io.github.ganyuke.peoplehunt.paper.utils

import io.github.ganyuke.peoplehunt.core.events.models.FluidState
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.Location
import org.bukkit.entity.Player
import kotlin.time.Instant

/** Feet and eye block cells for a player at their current position. */
data class PlayerBodyBlocks(
    val feet: Block,
    val head: Block,
)

/**
 * Environmental conditions from head (eye cell) and feet (loc cell) block samples.
 *
 * Submerged = head block is the fluid; wading = feet in the fluid, head not.
 * Shared by snapshot polling and fluid transition listeners.
 */
data class PlayerEnvironmentSnapshot(
    val body: PlayerBodyBlocks,
    val isSubmergedInWater: Boolean,
    val isWadingInWater: Boolean,
    val isSubmergedInLava: Boolean,
    val isWadingInLava: Boolean,
    val isSuffocating: Boolean,
    val isInsideCobweb: Boolean,
    val isInsideSweetBerry: Boolean,
) {
    val isInLava: Boolean
        get() = isSubmergedInLava || isWadingInLava

    fun primaryFluidState(since: Instant): FluidState = when {
        isSubmergedInWater -> FluidState.SubmergedInWater(since)
        isInLava -> FluidState.InLava(since)
        isSuffocating -> FluidState.SuffocatingInBlock(since)
        else -> FluidState.Dry
    }
}

fun Player.bodyBlocks(): PlayerBodyBlocks = PlayerBodyBlocks(
    feet = location.block,
    head = eyeLocation.block,
)

fun Player.environmentSnapshot(): PlayerEnvironmentSnapshot {
    val body = bodyBlocks()
    val headInWater = body.head.type == Material.WATER
    val feetInWater = body.feet.type == Material.WATER
    val headInLava = body.head.type == Material.LAVA
    val feetInLava = body.feet.type == Material.LAVA
    return PlayerEnvironmentSnapshot(
        body = body,
        isSubmergedInWater = headInWater,
        isWadingInWater = feetInWater && !headInWater,
        isSubmergedInLava = headInLava,
        isWadingInLava = feetInLava && !headInLava,
        isSuffocating = eyeLocation.isInsideSuffocatingBlock(),
        isInsideCobweb = body.feet.type == Material.COBWEB,
        isInsideSweetBerry = body.feet.type == Material.SWEET_BERRY_BUSH,
    )
}

/**
 * Java Edition suffocation: the eyeline must intersect a block's collision shape.
 * See [BlockData.getCollisionShape] and [VoxelShape.getBoundingBoxes].
 */
private fun Location.isInsideSuffocatingBlock(): Boolean {
    val block = block
    if (block.isPassable) return false

    val shape = block.blockData.getCollisionShape(block.location)
    val localX = x - block.x
    val localY = y - block.y
    val localZ = z - block.z
    return shape.boundingBoxes.any { it.contains(localX, localY, localZ) }
}
