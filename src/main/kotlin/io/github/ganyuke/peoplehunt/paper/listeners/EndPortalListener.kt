package io.github.ganyuke.peoplehunt.paper.listeners

import io.github.ganyuke.peoplehunt.core.events.ReportableEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.paper.utils.post
import io.github.ganyuke.peoplehunt.paper.utils.toPos4
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.data.type.EndPortalFrame
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent

/**
 * Specialized listener for the End Portal completion milestone since
 * Paper doesn't expose the End Portal opening as a proper event.
 */
class EndPortalListener(private val inbound: ReportableEventBus) : Listener {
    // vanilla always puts frames at these offsets
    private val frameOffsets = arrayOf(
        Pair(-1, -2), Pair(0, -2), Pair(1, -2), // north
        Pair(-1, 2),  Pair(0, 2),  Pair(1, 2),  // south
        Pair(2, -1),  Pair(2, 0),  Pair(2, 1),   // east
        Pair(-2, -1), Pair(-2, 0), Pair(-2, 1) // west
    )

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return

        // verify the clicked block is an End Portal Frame
        val clickedBlock = event.clickedBlock ?: return
        val frameData = clickedBlock.blockData as? EndPortalFrame ?: return

        // very player is even holding an Ender Eye
        val itemInHand = event.hand?.let { event.player.inventory.getItem(it) } ?: return
        if (frameData.hasEye() || itemInHand.type != Material.ENDER_EYE) return

        // verify it's a valid end portal (also checks for completion)
        val center = getCompleteCenter(clickedBlock) ?: return

        inbound.post(ReportablePayload.EndPortalCompleted(center.toPos4()))
    }

    /**
     * scans 5x5 to locate the center and also counts the eyes
     * returns the location of the center if there are 11 eyes
     */
    private fun getCompleteCenter(clickedBlock: Block): Location? {
        val world = clickedBlock.world

        for (offsetX in -2..2) {
            for (offsetZ in -2..2) {
                val potentialCenter = world.getBlockAt(clickedBlock.x + offsetX, clickedBlock.y, clickedBlock.z + offsetZ)

                var validFramesCount = 0
                var eyesCount = 0

                for ((x, z) in frameOffsets) {
                    val block = world.getBlockAt(potentialCenter.x + x, potentialCenter.y, potentialCenter.z + z)

                    if (block.type == Material.END_PORTAL_FRAME) {
                        validFramesCount++
                        if ((block.blockData as? EndPortalFrame)?.hasEye() == true) {
                            eyesCount++
                        }
                    } else {
                        break
                    }
                }

                if (validFramesCount == 12 && eyesCount == 11) {
                    return potentialCenter.location
                }
            }
        }
        return null
    }

}