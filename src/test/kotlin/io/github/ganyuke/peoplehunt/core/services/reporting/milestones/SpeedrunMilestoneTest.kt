package io.github.ganyuke.peoplehunt.core.services.reporting.milestones

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SpeedrunMilestoneTest {
    @Test
    fun dedupKey_forItemAcquired_usesItem() {
        val milestone = SpeedrunMilestone.ItemAcquired(
            SpeedrunMilestone.ItemAcquired.Item.EYE_OF_ENDER,
            SpeedrunMilestone.AcquisitionMethod.TRADED,
        )
        assertEquals(
            SpeedrunMilestone.DedupKey.Item(SpeedrunMilestone.ItemAcquired.Item.EYE_OF_ENDER),
            milestone.dedupKey,
        )
    }

    @Test
    fun dedupKey_forDragonSlain_isShared() {
        assertEquals(
            SpeedrunMilestone.DedupKey.DragonSlain,
            SpeedrunMilestone.DragonSlain.ByRunner.dedupKey,
        )
        assertIs<SpeedrunMilestone.DedupKey.DragonSlain>(
            SpeedrunMilestone.DragonSlain.ByOther("minecraft:wither").dedupKey,
        )
    }

    @Test
    fun dedupKey_forUniqueMilestones_wrapsInstance() {
        val milestone = SpeedrunMilestone.EnteredStronghold
        val key = SpeedrunMilestone.DedupKey.Unique(milestone)
        assertEquals(key, milestone.dedupKey)
        assertEquals(key, key.copy())
        key.hashCode()
    }

    @Test
    fun dedupKey_itemAndDragonVariants_supportEquality() {
        val itemKey = SpeedrunMilestone.DedupKey.Item(SpeedrunMilestone.ItemAcquired.Item.BUCKET)
        assertEquals(itemKey, itemKey.copy())
        val dragon = SpeedrunMilestone.DragonSlain.ByOther("minecraft:wither")
        assertEquals(SpeedrunMilestone.DedupKey.DragonSlain, dragon.dedupKey)
        assertEquals(dragon, dragon.copy())
    }
}
