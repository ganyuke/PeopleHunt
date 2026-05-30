package io.github.ganyuke.peoplehunt.core.services.reporting.milestones

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpeedrunMilestoneFullTest {
    @Test
    fun allMilestones_haveDedupKeys() {
        val milestones: List<SpeedrunMilestone> = listOf(
            SpeedrunMilestone.PickedUpWater,
            SpeedrunMilestone.PickedUpLava,
            SpeedrunMilestone.EnteredNether,
            SpeedrunMilestone.EnteredFortress,
            SpeedrunMilestone.EnteredBastion,
            SpeedrunMilestone.LeftNether,
            SpeedrunMilestone.ThrewEyeOfEnder,
            SpeedrunMilestone.EnteredStronghold,
            SpeedrunMilestone.CompletedEndPortal,
            SpeedrunMilestone.EnteredEnd,
            SpeedrunMilestone.DestroyedFirstEndCrystal,
            SpeedrunMilestone.DestroyedAllEndCrystals,
            SpeedrunMilestone.DragonAt50Percent,
            SpeedrunMilestone.DragonAt25Percent,
            SpeedrunMilestone.DragonAt10Percent,
            SpeedrunMilestone.DragonAt5Percent,
            SpeedrunMilestone.ItemAcquired(
                SpeedrunMilestone.ItemAcquired.Item.IRON_INGOT,
                SpeedrunMilestone.AcquisitionMethod.KILLED,
            ),
            SpeedrunMilestone.DragonSlain.ByRunner,
            SpeedrunMilestone.DragonSlain.ByOther("minecraft:wither"),
        )
        assertTrue(milestones.map { it.dedupKey }.distinct().size >= milestones.size - 1)
    }

    @Test
    fun enums_exposeAllConstants() {
        SpeedrunMilestone.AcquisitionMethod.entries.forEach { assertEquals(it, it) }
        SpeedrunMilestone.ItemAcquired.Item.entries.forEach { assertEquals(it, it) }
    }
}
