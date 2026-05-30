package io.github.ganyuke.peoplehunt.core.services.reporting.milestones

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MilestoneTrackerTest {
    @Test
    fun trackMilestone_recordsOnce() {
        val tracker = MilestoneTracker()
        assertTrue(tracker.trackMilestone(SpeedrunMilestone.EnteredNether))
        assertFalse(tracker.trackMilestone(SpeedrunMilestone.EnteredNether))
        assertEquals(1, tracker.milestoneRecords.size)
        assertEquals(SpeedrunMilestone.EnteredNether, tracker.milestoneRecords.single().milestone)
    }

    @Test
    fun trackMilestone_dedupesItemAcquiredByItem() {
        val tracker = MilestoneTracker()
        val first = SpeedrunMilestone.ItemAcquired(
            SpeedrunMilestone.ItemAcquired.Item.BLAZE_ROD,
            SpeedrunMilestone.AcquisitionMethod.CRAFTED,
        )
        val second = SpeedrunMilestone.ItemAcquired(
            SpeedrunMilestone.ItemAcquired.Item.BLAZE_ROD,
            SpeedrunMilestone.AcquisitionMethod.PICKED_UP,
        )
        assertTrue(tracker.trackMilestone(first))
        assertFalse(tracker.trackMilestone(second))
    }

    @Test
    fun milestoneRecord_exposesFields() {
        val tracker = MilestoneTracker()
        tracker.trackMilestone(SpeedrunMilestone.EnteredEnd)
        val record = tracker.milestoneRecords.single()
        assertEquals(SpeedrunMilestone.EnteredEnd, record.milestone)
        assertEquals(record, record.copy())
        record.recordedAt
    }

    @Test
    fun clear_removesRecords() {
        val tracker = MilestoneTracker()
        tracker.trackMilestone(SpeedrunMilestone.PickedUpWater)
        tracker.clear()
        assertTrue(tracker.milestoneRecords.isEmpty())
    }
}
